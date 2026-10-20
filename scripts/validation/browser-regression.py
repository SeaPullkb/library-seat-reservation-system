#!/usr/bin/env python3
"""End-to-end registration, profile, booking, lifecycle, admin, statistics and UI regression.
The external Java harness is compiled under .local only; no test clock endpoint enters production.
Requires Java 17, MySQL root access, Playwright and the ignored local .env.
"""
from pathlib import Path
import argparse, importlib.util, os, re, json, time, uuid, subprocess, secrets, platform
from playwright.sync_api import sync_playwright, expect
ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('library_demo', ROOT / 'scripts/demo.py')
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)

def main():
    demo.configure_stdio()
    parser = argparse.ArgumentParser(description='Run real browser regression in a temporary MySQL database; never uses the normal business database.')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--mysql-client', type=Path, help='Native MySQL root client; default uses project Compose.')
    parser.add_argument('--port', type=int, default=8081)
    parser.add_argument('--no-build', action='store_true')
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error('invalid validation port')
    repo = ROOT
    base = ROOT / '.local/validation'
    base.mkdir(parents=True, exist_ok=True)
    demo.port_free(args.port)
    bindings = demo.bindings(repo / '.env')
    run = base / uuid.uuid4().hex[:12]
    run.mkdir()
    shots = args.output or run / 'screenshots'
    shots.mkdir(parents=True, exist_ok=True)
    if not args.no_build:
        demo.build()
    cp_file = run / 'classpath.txt'
    wrapper = [str(repo / 'mvnw')] if os.name != 'nt' else ['cmd', '/c', str(repo / 'mvnw.cmd')]
    with (run / 'build.log').open('w', encoding='utf-8') as build_log:
        subprocess.run(wrapper + ['-B', '-ntp', 'dependency:build-classpath', '-Dmdep.outputFile=' + str(cp_file)], cwd=repo, check=True, stdout=build_log, stderr=subprocess.STDOUT)
        runtime_cp = str(repo / 'target/classes') + os.pathsep + cp_file.read_text(encoding='utf-8').strip()
        harness = run / 'harness'
        harness.mkdir()
        subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-cp', runtime_cp, '-d', str(harness), str(ROOT / 'scripts/validation/BrowserHarness.java')], check=True, cwd=repo, stdout=build_log, stderr=subprocess.STDOUT)
    database = 'delivery_browser_' + uuid.uuid4().hex[:8]
    username = bindings.get('DB_USERNAME', 'library')
    assert re.fullmatch('[A-Za-z0-9_]+', username)
    url = 'http://127.0.0.1:' + str(args.port)
    metadata = run / 'metadata.json'
    ack = run / 'ack.json'
    sequence = 0
    process = None
    created = False
    next_password = secrets.token_urlsafe(20)
    errors = []
    server_errors = []
    csp_violations = []
    screens = []

    def sql(statement):
        return demo.mysql(bindings, statement, args.mysql_client)

    def wait_for(test, timeout=25):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            if process and process.poll() is not None:
                raise RuntimeError('Temporary app stopped; inspect ' + str(run / 'application.log'))
            try:
                result = test()
                if result:
                    return result
            except (FileNotFoundError, json.JSONDecodeError):
                pass
            time.sleep(0.05)
        raise TimeoutError('Temporary harness operation timed out')

    def command(line):
        nonlocal sequence
        sequence += 1
        process.stdin.write(line + '\n')
        process.stdin.flush()
        return wait_for(lambda: data if (data := json.loads(ack.read_text(encoding='utf-8'))).get('sequence') == sequence else None)

    def login(page, username, password=None, home='/student'):
        page.goto(url + '/login')
        page.get_by_label('账号', exact=True).fill(username)
        page.get_by_label('密码', exact=True).fill(password or bindings['APP_DEMO_PASSWORD'])
        page.get_by_role('button', name='登录', exact=True).click()
        expect(page).to_have_url(url + home)

    def book(page, seat):
        previous = page.url
        page.get_by_role('button', name='预约座位' + seat, exact=True).click()
        expect(page).to_have_url(previous)
        expect(page.locator('[data-selected-seat]')).to_have_text(seat)
        if seat == 'A01':
            capture(page, '21-seat-selected')
        page.get_by_role('button', name='确认预约', exact=True).click()
        expect(page).to_have_url(url + '/student/reservations')

    def capture(page, name):
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), name + ' page overflow'
        assert page.locator('main h1').count() == 1, name + ' main heading'
        unlabeled = page.locator('input:not([type=hidden]), select, textarea').evaluate_all("elements => elements.filter(e => !e.labels?.length && !e.getAttribute('aria-label') && !e.getAttribute('aria-labelledby')).map(e => e.name)")
        assert not unlabeled, (name, 'unlabeled controls', unlabeled)
        violations = page.evaluate('window.__cspViolations || []')
        assert not violations, (name, violations)
        csp_violations.extend(violations)
        page.screenshot(path=str(shots / (name + '.png')), full_page=True)
        screens.append(name)
        if 'mobile' not in name:
            viewport = page.viewport_size
            page.set_viewport_size({'width': 390, 'height': 844})
            page.wait_for_timeout(200)
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), name + ' mobile overflow'
            page.screenshot(path=str(shots / (name + '-mobile.png')), full_page=True)
            screens.append(name + '-mobile')
            page.set_viewport_size(viewport)
    try:
        sql(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON `{database}`.* TO '{username}'@'%';")
        created = True
        cp = runtime_cp + os.pathsep + str(harness)
        environment = os.environ.copy()
        environment.update(bindings)
        environment['DB_NAME'] = database
        environment['SERVER_PORT'] = str(args.port)
        log = (run / 'application.log').open('w')
        process = subprocess.Popen(['java', '-Dfile.encoding=UTF-8', '-cp', cp, 'validation.BrowserHarness', str(metadata), str(ack)], stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, env=environment, text=True, encoding='utf-8', cwd=repo)
        fixture = wait_for(lambda: json.loads(metadata.read_text(encoding='utf-8')), 60)
        room = fixture['room']
        grid = url + f'/student/rooms/{room}/seats?date=2030-05-08&slot=1'
        with sync_playwright() as p:
            executable = os.environ.get('CHROMIUM_EXECUTABLE') or ('/usr/bin/chromium' if Path('/usr/bin/chromium').is_file() else None)
            browser = p.chromium.launch(**{'executable_path': executable, 'args': ['--no-sandbox']} if executable else {})
            contexts = [browser.new_context(viewport={'width': 1280, 'height': 900}, locale='zh-CN') for _ in range(4)]
            for context in contexts:
                context.add_init_script("window.__cspViolations=[];document.addEventListener('securitypolicyviolation',e=>window.__cspViolations.push(e.violatedDirective));")
            a, b, librarian, admin = [context.new_page() for context in contexts]
            for page in [a, b, librarian, admin]:
                page.on('dialog', lambda dialog: dialog.accept())
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.on('response', lambda response: server_errors.append((response.status, response.url)) if response.status >= 500 else None)
            a.goto(url + '/login')
            capture(a, '01-login')
            a.get_by_role('link', name='注册学生账号', exact=True).click()
            capture(a, '02-register')
            for field, value in [('username', 'graduate_student'), ('password', bindings['APP_DEMO_PASSWORD']), ('confirmPassword', bindings['APP_DEMO_PASSWORD']), ('realName', '毕业验收同学'), ('className', '计算机毕业班'), ('phone', '13912345678')]:
                a.locator('#' + field).fill(value)
            a.get_by_role('button', name='创建账号', exact=True).click()
            login(a, 'graduate_student')
            capture(a, '03-student-home')
            login(b, 'graduate_student')
            a.get_by_role('navigation', name='功能导航', exact=True).get_by_role('link', name='个人中心', exact=True).click()
            a.locator('#className').fill('毕业设计验收班')
            a.get_by_role('button', name='保存个人资料', exact=True).click()
            expect(a.locator('#className')).to_have_value('毕业设计验收班')
            a.locator('#currentPassword').fill('Incorrect-password-123!')
            a.locator('#password').fill(next_password)
            a.locator('#confirmPassword').fill(next_password)
            a.get_by_role('button', name='修改密码并重新登录', exact=True).click()
            expect(a.get_by_role('alert')).to_contain_text('当前密码不正确')
            a.locator('#currentPassword').fill(bindings['APP_DEMO_PASSWORD'])
            a.locator('#password').fill(next_password)
            a.locator('#confirmPassword').fill(next_password)
            a.get_by_role('button', name='修改密码并重新登录', exact=True).click()
            expect(a).to_have_url(url + '/login?changed')
            b.goto(url + '/student/profile')
            expect(b).to_have_url(url + '/login?changed')
            login(a, 'graduate_student', next_password)
            a.goto(url + '/student/profile')
            capture(a, '04-profile')
            a.set_viewport_size({'width': 390, 'height': 844})
            capture(a, '14-profile-mobile')
            a.set_viewport_size({'width': 1280, 'height': 900})
            a.get_by_role('navigation', name='功能导航', exact=True).get_by_role('link', name='阅览室与选座', exact=True).click()
            capture(a, '05-rooms')
            a.get_by_role('link', name='查看第一阅览室的座位', exact=True).click()
            capture(a, '06-seats')
            a.set_viewport_size({'width': 390, 'height': 844})
            capture(a, '15-seats-mobile')
            a.set_viewport_size({'width': 1280, 'height': 900})
            book(a, 'A01')
            expect(a.get_by_role('row').filter(has_text='A01')).to_contain_text('待签到')
            a.goto(url + '/student')
            expect(a.locator('[data-metric=active]')).to_have_text('1')
            expect(a.locator('[data-metric=available]')).to_have_text('19')
            capture(a, '19-student-pending')
            a.goto(url + '/student/reservations')
            command('TIME 2030-05-08T07:45:00')
            a.reload()
            a.get_by_role('button', name='签到预约').click()
            expect(a.get_by_role('row').filter(has_text='A01')).to_contain_text('使用中')
            capture(a, '07-my-reservations')
            a.goto(url + '/student')
            capture(a, '20-student-using')
            login(librarian, 'librarian', home='/admin')
            expect(librarian.locator('[data-admin-metric=using]')).to_have_text('1')
            capture(librarian, '22-admin-live')
            a.goto(url + '/student/reservations')
            command('TIME 2030-05-08T09:00:00')
            a.reload()
            a.get_by_role('button', name='签退预约').click()
            expect(a.get_by_role('row').filter(has_text='A01')).to_contain_text('已完成')
            login(b, 'student')
            b.goto(grid)
            expect(b.locator('[data-seat-id]').filter(has_text='A01')).to_have_attribute('data-availability', 'OCCUPIED')
            book(b, 'A02')
            command('TIME 2030-05-08T09:10:01')
            assert command('SWEEP')['result']['expired'] == 1
            b.reload()
            expect(b.get_by_role('row').filter(has_text='A02')).to_contain_text('超时释放')
            b.goto(grid)
            book(b, 'A02')
            command('TIME 2030-05-08T09:20:01')
            b.reload()
            b.get_by_role('button', name='签到预约').click()
            command('TIME 2030-05-08T12:00:00')
            assert command('SWEEP')['result']['completed'] == 1
            b.reload()
            expect(b.get_by_role('row').filter(has_text='A02').filter(has_text='已完成')).to_contain_text('05-08 12:00:00')
            a.goto(url + f'/student/rooms/{room}/seats?date=2030-05-08&slot=2')
            book(a, 'A03')
            login(librarian, 'librarian', home='/admin')
            capture(librarian, '08-admin-home')
            librarian.get_by_role('navigation', name='功能导航', exact=True).get_by_role('link', name='阅览室与座位', exact=True).click()
            capture(librarian, '09-admin-rooms')
            librarian.locator('#q').fill('不存在')
            librarian.get_by_role('button', name='搜索', exact=True).click()
            expect(librarian.locator('tbody')).to_contain_text('暂无匹配')
            librarian.get_by_role('link', name='重置', exact=True).click()
            librarian.get_by_role('button', name='新增阅览室', exact=True).click()
            librarian.get_by_label('名称（必填）', exact=True).fill('验收备用阅览室')
            librarian.get_by_label('位置（必填）', exact=True).fill('四层')
            librarian.get_by_role('button', name='保存', exact=True).click()
            expect(librarian.get_by_role('status')).to_contain_text('已创建')
            librarian.get_by_role('link', name='编辑验收备用阅览室', exact=True).click()
            librarian.get_by_label('名称（必填）', exact=True).fill('验收阅览室（已编辑）')
            capture(librarian, '23-room-edit')
            librarian.get_by_role('button', name='保存', exact=True).click()
            librarian.get_by_role('button', name='停用验收阅览室（已编辑）', exact=True).click()
            expect(librarian.get_by_role('status')).to_contain_text('已停用')
            librarian.goto(url + f'/admin/rooms/{room}/seats')
            capture(librarian, '10-admin-seats')
            librarian.get_by_role('button', name='添加座位', exact=True).click()
            librarian.get_by_label('座位编号（必填）', exact=True).fill('A21')
            librarian.get_by_role('button', name='保存', exact=True).click()
            librarian.get_by_role('link', name='编辑座位A21', exact=True).click()
            librarian.get_by_label('座位编号（必填）', exact=True).fill('A22')
            capture(librarian, '24-seat-edit')
            librarian.get_by_role('button', name='保存', exact=True).click()
            librarian.get_by_role('button', name='停用座位A22', exact=True).click()
            librarian.get_by_role('button', name='启用座位A22', exact=True).click()
            librarian.get_by_role('button', name='删除座位A22', exact=True).click()
            expect(librarian.get_by_role('status')).to_contain_text('已删除')
            librarian.goto(url + '/admin/reservations?date=2030-05-08')
            row = librarian.get_by_role('row').filter(has_text='A03')
            row.get_by_role('button', name='取消预约').click()
            dialog = librarian.get_by_role('dialog')
            dialog.get_by_label('取消原因', exact=False).fill('毕业验收：未签到违规预约处理示例')
            dialog.get_by_role('button', name='确认取消预约', exact=True).click()
            expect(librarian.get_by_role('row').filter(has_text='A03')).to_contain_text('librarian')
            capture(librarian, '11-admin-reservations')
            librarian.goto(url + '/admin/statistics?date=2030-05-08&slot=1')
            librarian.wait_for_function("window.echarts && echarts.getInstanceByDom(document.getElementById('room-chart'))")
            dashboard = librarian.evaluate('window.libraryStatistics')
            assert dashboard['daily']['reservations'] == 4 and dashboard['daily']['students'] == 2 and (dashboard['daily']['checkinRate'] == 66.67) and (dashboard['daily']['expired'] == 1), dashboard
            assert dashboard['rooms'][0]['occupiedSeats'] == 2 and dashboard['rooms'][0]['reservationRate'] == 10, dashboard
            capture(librarian, '12-statistics')
            librarian.set_viewport_size({'width': 390, 'height': 844})
            librarian.wait_for_timeout(150)
            capture(librarian, '16-statistics-mobile')
            login(admin, 'admin', home='/admin')
            admin.get_by_role('navigation', name='功能导航', exact=True).get_by_role('link', name='用户管理', exact=True).click()
            capture(admin, '13-users')
            assert a.goto(url + '/admin/users').status == 403
            capture(a, '17-forbidden')
            admin.get_by_role('link', name='新增图书馆管理员', exact=True).click()
            for field, value in [('username', 'graduate_librarian'), ('password', bindings['APP_DEMO_PASSWORD']), ('realName', '验收图书馆管理员'), ('phone', '13912345678')]:
                admin.locator('#' + field).fill(value)
            admin.get_by_role('button', name='创建管理员', exact=True).click()
            expect(admin.get_by_role('heading', name='维护用户 graduate_librarian')).to_be_visible()
            capture(admin, '18-user-maintenance')
            fallback = browser.new_context(java_script_enabled=False, viewport={'width': 390, 'height': 844}).new_page()
            login(fallback, 'student')
            fallback.goto(url + f'/student/rooms/{room}/seats?date=2030-05-09&slot=1')
            fallback.get_by_role('button', name='预约座位A04', exact=True).click()
            expect(fallback).to_have_url(url + '/student/reservations')
            assert not errors, errors
            assert not server_errors, server_errors
            evidence = {'clock': '2030-05-08 (external validation Clock only)', 'coreScreens': 13, 'screens': screens, 'dashboard': dashboard, 'pages': len(screens), 'platform': platform.platform(), 'jsErrors': errors, 'cspViolations': csp_violations, 'serverErrors': server_errors}
            (shots / 'evidence.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
            browser.close()
        print('PASS: real registration/profile/current-password verification/session revocation -> booking/checkin/checkout -> expiry/rebooking/exact-deadline sign-in/auto completion -> audited admin cancellation -> exact statistics and user maintenance.')
        print('PASS: ' + str(len(screens)) + ' real desktop/mobile screenshots; strict CSP, JS, HTTP500, labels, headings and overflow checks passed. Only isolated temporary data and an external test Clock used.')
    except Exception as error:
        message = str(error).replace(next_password, '[redacted]')
        for key, value in bindings.items():
            if value and 'PASSWORD' in key:
                message = message.replace(value, '[redacted]')
        print(type(error).__name__ + ': ' + message)
        raise SystemExit(1)
    finally:
        if process and process.poll() is None:
            try:
                process.stdin.write('EXIT\n')
                process.stdin.flush()
                process.wait(timeout=12)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                process.terminate()
                process.wait(timeout=12)
        if created:
            sql(f"REVOKE ALL PRIVILEGES ON `{database}`.* FROM '{username}'@'%'; DROP DATABASE `{database}`;")
if __name__ == '__main__':
    main()
