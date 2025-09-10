export function setupSignalRoutes(client, { me, onSignal, subscribeDest }) {
    // subscribeDest 예) `/topic/signal.${me}`  ← 컨트롤러의 전송 목적지와 반드시 일치시킬 것
    const sub = client.subscribe(subscribeDest, (msg) => {
        const payload = JSON.parse(msg.body)
        onSignal(payload) // {type, senderLoginId, receiverLoginId, data}
    })

    function sendSignal(signal) {
        client.publish({
            destination: '/app/signal',     // @MessageMapping("/signal")
            body: JSON.stringify(signal)    // {type, senderLoginId, receiverLoginId, data}
        })
    }

    return { sub, sendSignal }
}
