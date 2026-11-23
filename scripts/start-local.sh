#!/usr/bin/env bash
# 映物 VIVID 本地一键启动脚本
#   ./scripts/start-local.sh up      启动 MySQL/Redis(如需) + 后端 + 用户端 + 管理端
#   ./scripts/start-local.sh status  查看各服务状态
#   ./scripts/start-local.sh down    停止由本脚本启动的进程
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG="$ROOT/.local-logs"
PID="$LOG/pids"
mkdir -p "$LOG" "$PID" "$ROOT/server/uploadPath"

c_green='\033[0;32m'; c_yellow='\033[0;33m'; c_red='\033[0;31m'; c_off='\033[0m'
say()  { printf "${c_green}[wimo]${c_off} %s\n" "$*"; }
warn() { printf "${c_yellow}[wimo]${c_off} %s\n" "$*"; }
die()  { printf "${c_red}[wimo]${c_off} %s\n" "$*" >&2; exit 1; }

http_ok() { curl -s -m 2 -o /dev/null -w '%{http_code}' "$1" 2>/dev/null | grep -q '^2'; }
port_up() { (echo > "/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

start_bg() {
  local name="$1"; shift
  ( cd "$ROOT" && exec "$@" ) >"$LOG/$name.log" 2>&1 &
  echo $! >"$PID/$name.pid"
  say "$name 已启动 (pid $(cat "$PID/$name.pid"))，日志：$LOG/$name.log"
}

stop_bg() {
  local name="$1"
  [[ -f "$PID/$name.pid" ]] || return 0
  local p; p="$(cat "$PID/$name.pid")"
  if kill -0 "$p" 2>/dev/null; then kill "$p" 2>/dev/null || true; fi
  rm -f "$PID/$name.pid"
  say "$name 已停止"
}

# ---------- 基础设施 ----------
ensure_infra() {
  if command -v mysqladmin >/dev/null && mysqladmin ping >/dev/null 2>&1; then
    say "复用本机 MySQL"
  elif command -v docker >/dev/null && docker compose version >/dev/null 2>&1; then
    warn "本机无 MySQL，改用 docker compose 启动 mysql + redis（首次会自动导入 server/sql 下的建表脚本）"
    docker compose -f "$ROOT/docker-compose.yml" up -d mysql redis
  else
    die "未检测到 MySQL。请安装 MariaDB/MySQL 与 Redis，或安装 Docker 后重试。"
  fi

  if command -v redis-cli >/dev/null && redis-cli ping 2>/dev/null | grep -q PONG; then
    say "复用本机 Redis"
  elif command -v docker >/dev/null && docker compose version >/dev/null 2>&1; then
    docker compose -f "$ROOT/docker-compose.yml" up -d redis
  else
    die "未检测到 Redis。请安装 Redis 或安装 Docker 后重试。"
  fi
  sleep 2
}

# ---------- 启动 ----------
do_up() {
  ensure_infra

  # 后端：没有 jar 就先构建
  local jar="$ROOT/server/ruoyi-admin/target/ruoyi-admin.jar"
  if [[ ! -f "$jar" ]]; then
    say "未找到后端 jar，开始 Maven 构建…"
    ( cd "$ROOT/server" && mvn -q -DskipTests package ) || die "后端构建失败"
  fi
  if ! port_up 8080; then
    start_bg server java -jar "$jar" \
      --ruoyi.profile="$ROOT/server/uploadPath" \
      --wimo.mock.sample-dir="$ROOT/web/public/images/preview"
  else
    warn "8080 已被占用，跳过后端启动"
  fi

  # 用户端 Nuxt
  [[ -d "$ROOT/web/node_modules" ]] || ( cd "$ROOT/web" && pnpm install )
  if ! port_up 3000; then
    start_bg web env NUXT_PUBLIC_API_BASE=http://localhost:8080 npx nuxt dev --port 3000
  else
    warn "3000 已被占用，跳过用户端启动"
  fi

  # 管理端 RuoYi-Vue3
  [[ -d "$ROOT/admin/node_modules" ]] || ( cd "$ROOT/admin" && pnpm install )
  if ! port_up 80; then
    start_bg admin npx vite --port 80 --host
  else
    warn "80 已被占用，跳过管理端启动"
  fi

  say "等待服务就绪…"
  for i in $(seq 1 40); do
    http_ok http://127.0.0.1:8080/api/wimo/task/health && break
    sleep 2
  done

  echo
  say "────────── 访问地址 ──────────"
  printf "  用户端 (中文)  http://localhost:3000/zh/ecommerce-form\n"
  printf "  用户端 (英文)  http://localhost:3000/en/ecommerce-form\n"
  printf "  管理端         http://localhost:80   (admin / admin123)\n"
  printf "  后端健康检查   http://localhost:8080/api/wimo/task/health\n"
  printf "  后端 Swagger   http://localhost:8080/swagger-ui.html\n"
  say "──────────────────────────────"
  do_status
}

do_status() {
  printf "\n%-14s %s\n" "服务" "状态"
  printf "%-14s %s\n" "MySQL(3306)" "$(port_up 3306 && echo '在线' || echo '离线')"
  printf "%-14s %s\n" "Redis(6379)" "$(port_up 6379 && echo '在线' || echo '离线')"
  printf "%-14s %s\n" "后端(8080)"  "$(http_ok http://127.0.0.1:8080/api/wimo/task/health && echo '在线' || echo '离线')"
  printf "%-14s %s\n" "用户端(3000)" "$(http_ok http://127.0.0.1:3000/zh/ecommerce-form && echo '在线' || echo '离线')"
  printf "%-14s %s\n" "管理端(80)"   "$(http_ok http://127.0.0.1:80/ && echo '在线' || echo '离线')"
}

do_down() {
  stop_bg admin
  stop_bg web
  stop_bg server
  say "MySQL / Redis 若由 docker 启动，可执行：docker compose down"
}

case "${1:-up}" in
  up)     do_up ;;
  down)   do_down ;;
  status) do_status ;;
  *) echo "用法: $0 {up|down|status}" ; exit 1 ;;
esac
