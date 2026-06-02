import router from '../router'

const AUTH_EXPIRED_EVENT = 'chatroom:auth-expired'
let handlingUnauthorized = false

export function clearAuthState() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
}

export function handleUnauthorized(message = '登录已失效，请重新登录') {
  clearAuthState()

  if (!handlingUnauthorized) {
    handlingUnauthorized = true
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, { detail: { message } }))
    if (router.currentRoute.value.path !== '/login') {
      router.push('/login')
    }
    window.setTimeout(() => {
      handlingUnauthorized = false
    }, 300)
  }
}

export function onAuthExpired(callback) {
  const listener = (event) => callback(event.detail?.message || '登录已失效，请重新登录')
  window.addEventListener(AUTH_EXPIRED_EVENT, listener)
  return () => window.removeEventListener(AUTH_EXPIRED_EVENT, listener)
}
