import axios from 'axios'
import { ElMessage } from 'element-plus'
import { handleUnauthorized } from '../utils/auth'

const TRACE_ID_STORAGE_KEY = 'traceId'

function generateId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID().replace(/-/g, '')
  }
  return `${Date.now()}${Math.random().toString(16).slice(2)}`
}

function getOrCreateTraceId() {
  let traceId = localStorage.getItem(TRACE_ID_STORAGE_KEY)
  if (!traceId) {
    traceId = generateId()
    localStorage.setItem(TRACE_ID_STORAGE_KEY, traceId)
  }
  return traceId
}

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  const traceId = getOrCreateTraceId()
  const requestId = generateId()

  config.headers['X-Trace-Id'] = traceId
  config.headers['X-Request-Id'] = requestId

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    if (res.code === 401) {
      handleUnauthorized(res.message || '登录已过期，请重新登录')
      ElMessage.error(res.message || '登录已过期，请重新登录')
      return Promise.reject(new Error(res.message))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  },
  error => {
    const authStatus = error.response?.headers?.['x-auth-status']
    if (error.response?.status === 401) {
      const messageMap = {
        expired: 'Token 已过期，请重新登录',
        invalid: 'Token 无效，请重新登录',
        missing: '请先登录'
      }
      const message = error.response?.data?.message || messageMap[authStatus] || '登录已过期，请重新登录'
      handleUnauthorized(message)
      ElMessage.error(message)
      return Promise.reject(error)
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
