import { subscribe, send } from './ws'

export const subscribeSignals = async (callback) => {
  return subscribe('/user/queue/signals', callback)
}

export const sendSignal = async (payload) => {
  await send('/app/signal', payload)
}
