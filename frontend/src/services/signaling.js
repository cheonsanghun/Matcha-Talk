export function setupSignalRoutes(client, { me, onSignal, subscribeDest } = {}) {
    const destination = subscribeDest || '/user/queue/signals'
    const sub = client.subscribe(destination, (msg) => {
        const payload = JSON.parse(msg.body)
        onSignal?.(payload)
    })

    function sendSignal(signal = {}) {
        client.publish({
            destination: '/app/signal',
            body: JSON.stringify({ senderLoginId: me, ...signal }),
        })
    }

    return { sub, sendSignal }
}
