import "./style.css";

const scenarios = {
    portal: {
        status: "인증 필요",
        wifi: "로그인 화면 감지",
        cell: "인터넷 사용 가능",
        guide: "이 와이파이를 쓰려면 접속 화면에서 인증하세요. 그렇지 않다면 인터넷 설정에서 모바일 데이터로 전환할 수 있습니다.",
    },
    weak: {
        status: "와이파이 신호 약함",
        wifi: "−82 dBm · 신호 약함",
        cell: "인터넷 사용 가능",
        guide: "와이파이에서 멀어지고 있다면 인터넷 설정을 열어 모바일 데이터로 바꿀 수 있습니다.",
    },
    mobile: {
        status: "모바일 데이터 사용 중",
        wifi: "연결되지 않음",
        cell: "현재 기본 연결",
        guide: "새 연결을 이미 사용하고 있습니다. 인터넷이 멈춘 앱은 해당 앱에서 새로고침하거나 다시 연결해 보세요.",
    },
};

const options = document.querySelector(".scenario-options");
const preview = document.querySelector(".scenario-output");
const fields = {
    status: document.querySelector("#scenario-status"),
    wifi: document.querySelector("#scenario-wifi"),
    cell: document.querySelector("#scenario-cell"),
    guide: document.querySelector("#scenario-guide"),
};

function showScenario(id) {
    const scenario = scenarios[id];

    if (!scenario) {
        return;
    }

    preview.dataset.state = id;
    for (const [key, element] of Object.entries(fields)) {
        element.textContent = scenario[key];
    }
}

options.addEventListener("change", (event) => {
    if (event.target.matches('input[name="scenario"]')) {
        showScenario(event.target.value);
    }
});

showScenario(options.querySelector(":checked")?.value ?? "portal");
options.disabled = false;
document.querySelector("#scenario-fallback").hidden = true;
