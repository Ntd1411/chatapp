import { config } from "./config.js";

// lấy user được gọi
const params = new URLSearchParams(window.location.search);
const friendId = params.get("to");

// socket
const socket = io(config.socketUrl, {
    auth: {
        token: localStorage.getItem("token")
    }
});

// WebRTC
const pc = new RTCPeerConnection({
    iceServers: [{ urls: "stun:stun.l.google.com:19302" }]
});

const localVideo = document.getElementById("localVideo");
const remoteVideo = document.getElementById("remoteVideo");

// lấy camera + mic
navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    .then(stream => {
        localVideo.srcObject = stream;
        stream.getTracks().forEach(track => pc.addTrack(track, stream));
    });

// nhận stream từ người kia
pc.ontrack = e => {
    remoteVideo.srcObject = e.streams[0];
};

// gửi ICE
pc.onicecandidate = e => {
    if (e.candidate) {
        socket.emit("call:ice", {
            to: friendId,
            candidate: e.candidate
        });
    }
};

// tạo offer (khi là người gọi)
async function startCall() {
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);

    socket.emit("call:offer", {
        to: friendId,
        offer
    });
}

startCall();

// nhận answer
socket.on("call:answer", async data => {
    await pc.setRemoteDescription(data.answer);
});

// nhận ICE
socket.on("call:ice", async data => {
    if (data.candidate) {
        await pc.addIceCandidate(data.candidate);
    }
});

// kết thúc
document.getElementById("endCallBtn").onclick = () => {
    socket.emit("call:end", { to: friendId });
    window.close();
};
