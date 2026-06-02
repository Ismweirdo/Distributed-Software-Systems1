import { defineStore } from 'pinia'
import { getPrivateHistory, getGroupHistory } from '../api/message'

export const useChatStore = defineStore('chat', {
  state: () => ({
    // messages grouped by conversation key: 'private_{userId}' or 'group_{groupId}'
    messages: {},
    currentChatKey: '',
    loading: false
  }),
  getters: {
    currentMessages: (state) => state.messages[state.currentChatKey] || []
  },
  actions: {
    mergeMessages(existingMessages = [], incomingMessages = []) {
      const merged = [...existingMessages]
      incomingMessages.forEach((message) => {
        const existing = merged.find(m =>
          (message.id && m.id === message.id) ||
          (message.clientMessageId && m.clientMessageId === message.clientMessageId)
        )
        if (existing) {
          Object.assign(existing, message)
          return
        }
        merged.push(message)
      })
      return merged
    },
    setCurrentChat(key) {
      this.currentChatKey = key
    },
    async fetchHistory(type, id, page = 1) {
      this.loading = true
      try {
        let msgs = []
        if (type === 'private') {
          msgs = await getPrivateHistory(id, page)
        } else {
          msgs = await getGroupHistory(id, page)
        }
        const key = `${type}_${id}`
        if (page === 1) {
          this.messages[key] = this.mergeMessages([], msgs)
        } else {
          this.messages[key] = this.mergeMessages(msgs, this.messages[key] || [])
        }
        return msgs
      } catch (e) {
        console.error('Failed to fetch history:', e)
        return []
      } finally {
        this.loading = false
      }
    },
    addMessage(key, message) {
      if (!this.messages[key]) {
        this.messages[key] = []
      }
      const existing = this.messages[key].find(m =>
        (message.id && m.id === message.id) ||
        (message.clientMessageId && m.clientMessageId === message.clientMessageId)
      )
      if (existing) {
        Object.assign(existing, message)
        return
      }
      this.messages[key].push(message)
    },
    updateMessage(key, messageId, updates) {
      const msgs = this.messages[key]
      if (msgs) {
        const idx = msgs.findIndex(m => m.id === messageId)
        if (idx >= 0) {
          Object.assign(msgs[idx], updates)
        }
      }
    },
    updateMessageStatus(key, messageId, updates) {
      const msgs = this.messages[key]
      if (msgs) {
        const msg = msgs.find(m => m.id === messageId)
        if (msg) {
          Object.assign(msg, updates)
        }
      }
    }
  }
})
