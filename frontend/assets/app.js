// 统一请求封装：自动带 token、统一错误提示
const API = '/api';

function getToken() {
  return localStorage.getItem('token') || '';
}
function setToken(t) {
  localStorage.setItem('token', t);
}
function clearToken() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
}

async function req(path, opt = {}) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, opt.headers || {});
  const t = getToken();
  if (t) headers['authorization'] = t;

  let res;
  try {
    res = await fetch(API + path, Object.assign({}, opt, { headers }));
  } catch (e) {
    throw new Error('网络异常，检查后端是否启动');
  }
  // 登录态失效：清 token 并回登录页（避免各页面各写一遍）
  if (res.status === 401) {
    clearToken();
    if (!/login\.html/.test(location.pathname)) {
      location.href = 'login.html';
    }
    throw new Error('登录已过期，请重新登录');
  }

  let json;
  try {
    json = await res.json();
  } catch (e) {
    throw new Error('返回格式异常：' + res.status);
  }
  if (json.success === false) throw new Error(json.errorMsg || '请求失败');
  return json.data;
}

// 轻提示
let toastTimer = null;
function toast(msg) {
  let el = document.querySelector('.toast');
  if (!el) {
    el = document.createElement('div');
    el.className = 'toast';
    document.body.appendChild(el);
  }
  el.textContent = msg;
  requestAnimationFrame(() => el.classList.add('show'));
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 2200);
}

// 未登录时跳登录页
function requireLogin() {
  if (!getToken()) {
    location.href = 'login.html?from=' + encodeURIComponent(location.pathname + location.search);
    return false;
  }
  return true;
}

function qs(name) {
  return new URLSearchParams(location.search).get(name);
}

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, c => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
}

// 门店图：后端返回 "/imgs/xxx.jpg"，nginx 已配置该路径
function shopImg(shop) {
  if (shop.images) {
    const first = String(shop.images).split(',')[0].trim();
    if (first) return first;
  }
  return '';
}

// 评分归一化：库里存的是 0-50 的整数（如 47 表示 4.7）
function fmtScore(v) {
  if (v == null || v === '') return null;
  const n = Number(v);
  if (!isFinite(n) || n <= 0) return null;
  return (n > 10 ? n / 10 : n).toFixed(1);
}
