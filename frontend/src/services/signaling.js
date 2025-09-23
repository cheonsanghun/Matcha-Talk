import { camelizeKeys, snakifyKeys } from '../utils/case'

export function setupSignalRoutes(client, { me, onSignal, subscribeDest } = {}) {
    const destination = subscribeDest || '/user/queue/signals'
    const sub = client.subscribe(destination, (msg) => {
        const raw = msg.body ? JSON.parse(msg.body) : null
        const payload = raw && typeof raw === 'object' ? camelizeKeys(raw) : raw
        onSignal?.(payload)
    })

    function sendSignal(signal = {}) {
        const payload = snakifyKeys({ senderLoginId: me, ...signal })
        client.publish({
            destination: '/app/signal',
            body: JSON.stringify(payload),
        })
    }

    return { sub, sendSignal }
}
