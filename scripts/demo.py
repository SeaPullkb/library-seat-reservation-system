#!/usr/bin/env python3
"""Create and run an isolated defense demo. Never seeds an existing database."""
from pathlib import Path
import argparse
import datetime as dt
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent
DEMO_ROOT = ROOT / '.local' / 'demos'
JAR_NAME = 'library-seat-reservation-system-0.1.0-SNAPSHOT.jar'


def configure_stdio():
    # Windows redirected stdout otherwise may use an ANSI code page for Chinese messages.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, 'reconfigure'):
            stream.reconfigure(encoding='utf-8')


def bindings(path):
    values = {}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        if line and not line.startswith('#') and '=' in line:
            name, value = line.split('=', 1)
            if not re.fullmatch('[A-Z][A-Z0-9_]*', name):
                raise ValueError('配置名称无效。')
            values[name] = value
    return values


def mysql(values, statement, client=None):
    environment = os.environ.copy()
    if client:
        environment['MYSQL_PWD'] = values.get('MYSQL_ROOT_PASSWORD', '')
        command = [str(client), '--default-character-set=utf8mb4', '-N', '-B', '--protocol=TCP',
                   '-h', values.get('DB_HOST', '127.0.0.1'), '-P', values.get('DB_PORT', '3306'), '-uroot']
    else:
        command = ['docker', 'compose', 'exec', '-T', 'mysql', 'sh', '-c',
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -uroot']
    result = subprocess.run(command, cwd=ROOT, env=environment, input=statement,
                            text=True, encoding='utf-8', capture_output=True)
    if result.returncode:
        raise RuntimeError('演示数据库操作失败；检查本机 MySQL 或 Compose 服务及本地配置。')
    return result.stdout.strip()


def valid_database(database):
    if not re.fullmatch(r'library_demo_[0-9]{8}_[a-f0-9]{8}', database):
        raise ValueError('拒绝操作非本脚本生成的独立演示库。')


def valid_user(values):
    user = values.get('DB_USERNAME', 'library')
    if not re.fullmatch('[A-Za-z0-9_]{1,32}', user):
        raise ValueError('演示脚本仅接受字母、数字、下划线的 MySQL 用户名。')
    return user


def read_manifest(path):
    path = path.resolve()
    if path.parent.parent != DEMO_ROOT.resolve() or path.name != 'manifest.json':
        raise ValueError('只接受本仓库 .local/demos/ 下的演示记录。')
    data = json.loads(path.read_text(encoding='utf-8'))
    valid_database(data['database'])
    if data['database'] != path.parent.name:
        raise ValueError('演示记录与目录不一致。')
    return path, data, bindings(path.parent / '.env')


def port_free(port):
    with socket.socket() as sock:
        sock.settimeout(.5)
        if sock.connect_ex(('127.0.0.1', port)) == 0:
            raise RuntimeError('演示端口正在使用；请选择其他 --port，不会结束未知进程。')


def stop(process):
    if process and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def launch(data, values, folder, seed=False, prepare=False):
    env = os.environ.copy()
    env.update(values)
    env.update(DB_NAME=data['database'], SPRING_PROFILES_ACTIVE='dev,demo',
               APP_DEMO_DATE=data['date'], APP_DEMO_TIME=data.get('time', '09:05'), SERVER_ADDRESS='127.0.0.1', SERVER_PORT=str(data['port']))
    args = ['java', '-Dfile.encoding=UTF-8', '-jar', str(folder / 'application.jar'),
            '--spring.profiles.active=dev,demo', '--app.demo-seed.enabled=' + str(seed).lower(),
            '--server.port=' + ('0' if prepare else str(data['port']))]
    if prepare:
        args.append('--app.reservation-scheduler.enabled=false')
    log = (folder / ('seed.log' if prepare else 'application.log')).open('a', encoding='utf-8')
    process = subprocess.Popen(args, cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                               stdout=log, stderr=subprocess.STDOUT)
    log.close()
    return process


def wait_seed(process, data, values, client):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError('演示应用初始化失败；详情见该演示目录的日志。')
        try:
            count = mysql(values, 'SELECT COUNT(*) FROM `' + data['database'] + '`.`user`;', client)
            if count == '27':
                return
        except RuntimeError:
            pass  # schema/transaction may not yet be ready; never treat a skipped check as success.
        time.sleep(.25)
    raise RuntimeError('演示初始化未在120秒内完成；请查看演示日志。')


def build():
    wrapper = ROOT / ('mvnw.cmd' if os.name == 'nt' else 'mvnw')
    command = (['cmd', '/c', str(wrapper)] if os.name == 'nt' else [str(wrapper)])
    subprocess.run(command + ['-B', '-ntp', '-DskipTests', 'package'], cwd=ROOT, check=True)


def main():
    configure_stdio()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', nargs='?', default='serve', choices=['serve', 'prepare', 'resume', 'remove'])
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--date', type=dt.date.fromisoformat)
    parser.add_argument('--time', type=dt.time.fromisoformat, help='Resume only: advance the fixed demo clock after stopping it.')
    parser.add_argument('--port', type=int)
    parser.add_argument('--no-build', action='store_true', help='Use an already built current jar (does not run tests).')
    parser.add_argument('--mysql-client', type=Path, help='Native mysql executable; default uses project Docker Compose.')
    args = parser.parse_args()
    if args.port is not None and not 1024 <= args.port <= 65535:
        parser.error('--port 须在1024到65535之间。')
    if args.action in ('resume', 'remove'):
        if not args.manifest:
            parser.error('此操作需要 --manifest。')
        path, data, values = read_manifest(args.manifest)
        client = args.mysql_client or data.get('mysqlClient')
        if args.action == 'remove':
            # Explicit remove action; database/owner identifiers are validated before SQL construction.
            user = valid_user(values)
            mysql(values, f"REVOKE ALL PRIVILEGES ON `{data['database']}`.* FROM '{user}'@'%'; DROP DATABASE `{data['database']}`;", client)
            data['removed'] = True
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
            print('已删除该独立演示库；本地日志和配置保留在忽略目录中。')
            return
        if data.get('removed') or not data.get('prepared'):
            raise ValueError('该演示库未就绪或已移除。')
        previous = dt.datetime.combine(dt.date.fromisoformat(data['date']), dt.time.fromisoformat(data.get('time', '09:05')))
        next_date = args.date or previous.date()
        next_time = args.time or previous.time()
        if next_time.tzinfo is not None or next_time.microsecond:
            raise ValueError('演示时间须为本地整秒时间。')
        next_value = dt.datetime.combine(next_date, next_time)
        if next_value < previous:
            raise ValueError('演示时钟只能向前推进，不回退已经保存的业务状态。')
        data.update(date=next_date.isoformat(), time=next_time.isoformat())
        data['port'] = args.port or data['port']
        port_free(data['port'])
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        process = launch(data, values, path.parent)
        print('演示应用启动中；端口 ' + str(data['port']) + '，固定业务时间 ' + data['date'] + ' ' + data.get('time', '09:05') + '。', flush=True)
        print('使用原本地 APP_DEMO_PASSWORD 登录 student / librarian / admin；Ctrl+C停止。', flush=True)
        try:
            if process.wait() != 0:
                raise RuntimeError('演示应用启动失败；请查看该目录 application.log。')
        finally:
            stop(process)
        return

    if args.time is not None:
        parser.error('--time 只用于停止应用后的 resume；新演示从09:05开始。')
    values = bindings(ROOT / '.env')
    user = valid_user(values)
    day = args.date or dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date()
    if not dt.date(1000, 1, 15) <= day <= dt.date(9999, 12, 30):
        raise ValueError('演示日期须能容纳前14天历史和后1天预约。')
    port = args.port or 8082
    if args.action != 'prepare':
        port_free(port)
    if not args.no_build:
        build()
    jar = ROOT / 'target' / JAR_NAME
    if not jar.is_file():
        raise RuntimeError('缺少应用 jar；请先构建。')
    database = 'library_demo_' + day.strftime('%Y%m%d') + '_' + uuid.uuid4().hex[:8]
    valid_database(database)
    folder = DEMO_ROOT / database
    folder.mkdir(parents=True)
    private = folder / '.env'
    with private.open('x', encoding='utf-8') as output:
        private.chmod(0o600)
        output.write(''.join(name + '=' + value + '\n' for name, value in values.items()))
    shutil.copyfile(jar, folder / 'application.jar')
    data = dict(database=database, date=day.isoformat(), time='09:05', port=port, prepared=False,
                mysqlClient=str(args.mysql_client) if args.mysql_client else None)
    path = folder / 'manifest.json'
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    created = False
    process = None
    try:
        mysql(values, f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON `{database}`.* TO '{user}'@'%';", args.mysql_client)
        created = True
        process = launch(data, values, folder, seed=True, prepare=args.action == 'prepare')
        wait_seed(process, data, values, args.mysql_client)
        data['prepared'] = True
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print('已生成 4 间阅览室、114 个座位、27 个账号及多日预约；仅写入新建演示库。', flush=True)
        print('Manifest: ' + str(path), flush=True)
        if args.action != 'prepare':
            print('演示端口 ' + str(port) + '；固定业务时间 ' + day.isoformat() + ' 09:05。Ctrl+C停止。', flush=True)
            print('使用本地 APP_DEMO_PASSWORD 登录 student / librarian / admin。', flush=True)
            if process.wait() != 0:
                raise RuntimeError('演示应用退出；请查看 application.log。')
    finally:
        stop(process)
        if created and not data['prepared']:
            mysql(values, f"REVOKE ALL PRIVILEGES ON `{database}`.* FROM '{user}'@'%'; DROP DATABASE `{database}`;", args.mysql_client)


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print('\n演示应用已停止；可用 resume 继续或显式 remove 移除演示库。')
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError) as error:
        # Do not echo environment values, SQL, or password-bearing subprocess arguments.
        print(type(error).__name__ + ': ' + ('构建失败，请检查 Maven 输出。' if isinstance(error, subprocess.CalledProcessError) else str(error)), file=sys.stderr)
        raise SystemExit(1)
