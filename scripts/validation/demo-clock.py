#!/usr/bin/env python3
"""Real packaged demo time-boundary regression; requires a fresh, stopped prepared demo."""
from pathlib import Path
import argparse
import importlib.util
import json
import os
import time
import urllib.request
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('library_demo', ROOT / 'scripts/demo.py')
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)
REDACTIONS = []


def main():
    demo.configure_stdio()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / '.local/demo-clock')
    args = parser.parse_args()
    path, data, values = demo.read_manifest(args.manifest)
    REDACTIONS.extend(v for k, v in values.items() if 'PASSWORD' in k and v)
    args.output.mkdir(parents=True, exist_ok=True)
    url = 'http://127.0.0.1:' + str(data['port'])
    day = data['date']
    process = None
    evidence = []

    def start(clock):
        nonlocal process
        demo.stop(process)
        demo.port_free(data['port'])
        data['time'] = clock
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        process = demo.launch(data, values, path.parent)
        until = time.monotonic() + 40
        while time.monotonic() < until:
            if process.poll() is not None:
                raise RuntimeError('Demo stopped: inspect its application.log.')
            try:
                if urllib.request.urlopen(url + '/login', timeout=1).status == 200:
                    return
            except OSError:
                pass
            time.sleep(.1)
        raise TimeoutError('Demo server did not become ready.')

    def login(page, username, home):
        page.goto(url + '/login')
        page.get_by_label('账号', exact=True).fill(username)
        page.get_by_label('密码', exact=True).fill(values['APP_DEMO_PASSWORD'])
        page.get_by_role('button', name='登录', exact=True).click()
        expect(page).to_have_url(url + home)

    def metrics(page, using, pending, expired):
        login(page, 'librarian', '/admin')
        for key, value in [('using', using), ('pending', pending), ('expired', expired)]:
            expect(page.locator('[data-admin-metric=' + key + ']')).to_have_text(str(value))
        evidence.append(dict(time=data['time'], using=using, pending=pending, expired=expired))

    def book(page, number):
        page.goto(url + f'/student/rooms/{room}/seats?date={day}&slot=1')
        page.get_by_role('button', name='预约座位' + number, exact=True).click()
        expect(page.locator('[data-selected-seat]')).to_have_text(number)
        page.get_by_role('button', name='确认预约', exact=True).click()
        expect(page).to_have_url(url + '/student/reservations')

    try:
        with sync_playwright() as p:
            executable = os.environ.get('CHROMIUM_EXECUTABLE') or ('/usr/bin/chromium' if Path('/usr/bin/chromium').is_file() else None)
            browser = p.chromium.launch(**({'executable_path': executable, 'args': ['--no-sandbox']} if executable else {}))
            a, b, admin = [browser.new_context(locale='zh-CN', viewport=dict(width=1280, height=900)).new_page() for _ in range(3)]
            start('09:05'); metrics(admin, 2, 3, 2)
            login(a, 'demo_student_24', '/student')
            a.goto(url + '/student/rooms?date=' + day + '&slot=1')
            room = a.locator('[data-room-id]').first.get_attribute('data-room-id')
            start('09:11'); metrics(admin, 2, 0, 5)
            login(a, 'demo_student_24', '/student'); book(a, 'A05')
            expect(a.locator('[data-active-reservation]')).to_contain_text('09:21:00')
            login(b, 'demo_student_23', '/student'); book(b, 'A06')
            start('09:21'); login(a, 'demo_student_24', '/student')
            a.goto(url + '/student/reservations'); a.get_by_role('button', name='签到预约').click()
            expect(a.locator('[data-active-reservation]')).to_contain_text('使用中')
            metrics(admin, 3, 1, 5)
            start('09:21:01'); metrics(admin, 3, 0, 6)
            login(b, 'demo_student_23', '/student'); book(b, 'A06')
            expect(b.locator('[data-active-reservation]')).to_contain_text('09:31:01')
            b.get_by_role('button', name='签到预约').click()
            expect(b.locator('[data-active-reservation]')).to_contain_text('使用中')
            start('12:00'); metrics(admin, 0, 0, 6)
            login(a, 'demo_student_24', '/student'); a.goto(url + '/student/reservations')
            expect(a.get_by_role('row').filter(has_text='A05')).to_contain_text('已完成')
            expect(a.get_by_role('row').filter(has_text='A05')).to_contain_text('12:00:00')
            a.screenshot(path=str(args.output / 'auto-completed.png'), full_page=True)
            admin.screenshot(path=str(args.output / 'after-period-end.png'), full_page=True)
            browser.close()
        (args.output / 'evidence.json').write_text(json.dumps(dict(clock='isolated demo', phases=evidence,
            checks=['normal expiry', 'released seat rebooking', '10-minute deadline', 'exact deadline inclusive sign-in',
                    'one-second-late expiry', 'second rebooking', 'period-end auto completion with exact checkout timestamp']), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print('PASS: packaged application expiry/rebooking/exact deadline/late expiry and period-end completion across five demo clock phases.')
    finally:
        demo.stop(process)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        message = str(error)
        for secret in REDACTIONS:
            message = message.replace(secret, '[redacted]')
        print(type(error).__name__ + ': ' + message[:4000])
        raise SystemExit(1)
