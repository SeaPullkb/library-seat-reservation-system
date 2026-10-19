#!/usr/bin/env python3
"""Inspect a freshly prepared demo with real Chromium; writes screenshots, never resets data."""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import platform
import time
import urllib.request
from playwright.sync_api import sync_playwright, expect

REDACTIONS = []
ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('library_demo', ROOT / 'scripts/demo.py')
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / '.local/demo-browser')
    args = parser.parse_args()
    manifest, data, private = demo.read_manifest(args.manifest)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    url = 'http://127.0.0.1:' + str(data['port'])
    day = data['date']
    secret = private['APP_DEMO_PASSWORD']
    REDACTIONS.extend(value for key,value in private.items() if 'PASSWORD' in key and value)
    errors, failures, screenshots = [], [], []
    deadline = time.monotonic() + 40
    while True:
        try:
            if urllib.request.urlopen(url + '/login', timeout=2).status == 200:
                break
        except OSError:
            if time.monotonic() >= deadline:
                raise RuntimeError('请先使用该 manifest 启动演示应用。')
            time.sleep(.2)

    def login(page, username, home):
        page.goto(url + '/login')
        page.get_by_label('账号', exact=True).fill(username)
        page.get_by_label('密码', exact=True).fill(secret)
        page.get_by_role('button', name='登录', exact=True).click()
        expect(page).to_have_url(url + home)

    def capture(page, name):
        for mobile in (False, True):
            page.set_viewport_size(dict(width=390, height=844) if mobile else dict(width=1280, height=900))
            page.wait_for_timeout(200)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), name + ' overflow'
            assert page.locator('main h1').count() == 1, name + ' heading'
            assert not page.evaluate('window.__cspViolations || []'), name + ' CSP'
            controls = page.locator('input:not([type=hidden]), select, textarea').evaluate_all("es=>es.filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')&&!e.getAttribute('aria-labelledby')).map(e=>e.name)")
            assert not controls, (name, 'unlabeled controls', controls)
            filename = name + ('-mobile' if mobile else '') + '.png'
            page.screenshot(path=str(output / filename), full_page=True)
            screenshots.append(filename)
        page.set_viewport_size(dict(width=1280, height=900))
        page.wait_for_timeout(150)

    with sync_playwright() as p:
        executable = os.environ.get('CHROMIUM_EXECUTABLE')
        if not executable and Path('/usr/bin/chromium').is_file():
            executable = '/usr/bin/chromium'
        browser = p.chromium.launch(**({'executable_path': executable, 'args': ['--no-sandbox']} if executable else {}))
        pages = []
        for _ in range(5):
            context = browser.new_context(viewport=dict(width=1280, height=900), locale='zh-CN')
            context.add_init_script("window.__cspViolations=[];document.addEventListener('securitypolicyviolation',e=>window.__cspViolations.push(e.violatedDirective));")
            page = context.new_page()
            page.on('pageerror', lambda e: errors.append(str(e)))
            page.on('response', lambda r: failures.append([r.status, r.url]) if r.status >= 500 else None)
            page.on('dialog', lambda d: d.accept())
            pages.append(page)
        student, picker, librarian, admin, guest = pages
        guest.goto(url + '/login'); capture(guest, '01-login')
        guest.goto(url + '/register'); capture(guest, '02-register')
        login(student, 'student', '/student'); capture(student, '03-student-home')
        expect(student.locator('[data-metric=available]')).to_have_text('80')
        student.goto(url + '/student/profile'); capture(student, '04-profile')
        student.goto(url + '/student/rooms?date=' + day + '&slot=1'); capture(student, '05-rooms')
        expect(student.locator('[data-room-id]')).to_have_count(3)
        href = student.locator('[data-room-id]').first.get_by_role('link').get_attribute('href')
        room = student.locator('[data-room-id]').first.get_attribute('data-room-id')
        student.goto(url + href); capture(student, '26-seats-my-reservation')
        expect(student.locator('[data-seat-count=MINE]')).to_have_text('1')
        login(picker, 'demo_student_24', '/student')
        picker.goto(url + '/student/reservations'); capture(picker, '31-empty-reservations')
        picker.goto(url + href)
        expect(picker.locator('[data-seat-count=FREE]')).to_have_text('15')
        picker.get_by_role('button', name='预约座位A07', exact=True).click()
        expect(picker.locator('[data-selected-seat]')).to_have_text('A07')
        capture(picker, '06-seats')  # Selection preview only; no reservation is submitted here.
        student.goto(url + '/student/reservations'); capture(student, '24-my-reservations-pending')
        login(librarian, 'librarian', '/admin'); capture(librarian, '08-admin-home')
        for key, value in [('using', '2'), ('pending', '3'), ('expired', '2'), ('students', '9')]:
            expect(librarian.locator('[data-admin-metric=' + key + ']')).to_have_text(value)
        librarian.goto(url + '/admin/rooms'); capture(librarian, '09-admin-rooms')
        librarian.locator('#q').fill('不存在'); librarian.get_by_role('button', name='搜索', exact=True).click()
        expect(librarian.locator('tbody')).to_contain_text('暂无匹配'); capture(librarian, '32-empty-search')
        librarian.goto(url + f'/admin/rooms/{room}/seats'); capture(librarian, '10-admin-seats')
        librarian.get_by_role('button', name='添加座位', exact=True).click()
        librarian.get_by_label('座位编号（必填）', exact=True).fill('A01')
        librarian.get_by_role('button', name='保存', exact=True).click()
        expect(librarian.locator('.modal.show [role=alert]')).to_contain_text('已存在')
        expect(librarian.locator('.modal.show #seatNo')).to_have_attribute('aria-invalid', 'true')
        capture(librarian, '36-seat-validation-error')
        librarian.goto(url + '/admin/reservations?date=' + day); capture(librarian, '11-admin-reservations')
        librarian.locator('tbody details').first.locator('summary').click(); capture(librarian, '25-reservation-detail')
        librarian.goto(url + '/admin/statistics?date=' + day + '&slot=1')
        librarian.wait_for_function("window.echarts && echarts.getInstanceByDom(document.getElementById('room-chart'))")
        stats = librarian.evaluate('window.libraryStatistics')
        assert stats['daily']['reservations'] == 11 and stats['daily']['students'] == 9 and stats['daily']['checkinRate'] == 66.67, stats
        capture(librarian, '12-statistics')
        librarian.goto(url + '/admin/statistics?date=2020-01-01&slot=1')
        expect(librarian.get_by_role('status').filter(has_text='所选使用日期')).to_be_visible(); capture(librarian, '30-empty-statistics')
        login(admin, 'admin', '/admin'); admin.goto(url + '/admin/users'); capture(admin, '13-users')
        admin.get_by_role('link', name='下一页', exact=True).click(); capture(admin, '33-users-page2')
        admin.goto(url + '/admin/users?keyword=demo_student_24')
        target = admin.get_by_role('link', name='维护用户demo_student_24', exact=True)
        target.click(); capture(admin, '18-user-maintenance')
        assert student.goto(url + '/admin/users').status == 403; capture(student, '17-forbidden')
        assert student.goto(url + f'/student/rooms/{room}/seats?date=2020-01-01&slot=1').status == 400; capture(student, '34-bad-request')
        guest.goto(url + '/login'); guest.get_by_label('账号', exact=True).fill('unknown_demo_account')
        guest.get_by_label('密码', exact=True).fill('Incorrect-demo-login-123!'); guest.get_by_role('button', name='登录', exact=True).click()
        expect(guest.get_by_role('alert')).to_contain_text('账号或密码错误'); capture(guest, '35-login-error')
        student.goto(url + '/student/reservations')
        current = student.locator('[data-active-reservation]').filter(has_text='A01')
        current.get_by_role('button', name='签到预约').click()
        expect(student.locator('[data-active-reservation]').filter(has_text='A01')).to_contain_text('使用中')
        capture(student, '07-my-reservations')
        librarian.goto(url + '/admin')
        expect(librarian.locator('[data-admin-metric=using]')).to_have_text('3')
        expect(librarian.locator('[data-admin-metric=pending]')).to_have_text('2'); capture(librarian, '39-admin-after-checkin')
        student.locator('[data-active-reservation]').filter(has_text='A01').get_by_role('button', name='签退预约').click()
        expect(student.get_by_role('status')).to_contain_text('签退成功'); capture(student, '40-my-reservations-completed')
        assert not errors, errors
        assert not failures, failures
        browser.close()
    evidence = dict(environment=platform.platform(), date=day, clock='09:05 Asia/Shanghai (isolated demo)',
                    scenario='fresh isolated demo -> real check-in/check-out; no production data',
                    pages=len(screenshots), jsErrors=errors, http500=failures, statistics=stats,
                    runtimeJarSha256=hashlib.sha256((manifest.parent / 'application.jar').read_bytes()).hexdigest(),
                    screenshots={name: hashlib.sha256((output / name).read_bytes()).hexdigest() for name in screenshots})
    (output / 'manifest.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS: isolated demo, live dashboard changes, selection, forms, empty/error states, pagination and ' + str(len(screenshots)) + ' real desktop/mobile screenshots.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Assertions may contain DOM text. Never output passwords used in form inputs.
        message = str(error)
        for secret in REDACTIONS:
            message = message.replace(secret, '[redacted]')
        print(type(error).__name__ + ': ' + message[:4000])
        raise SystemExit(1)
