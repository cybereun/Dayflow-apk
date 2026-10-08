const EMAIL = 'cybereunny@gmail.com';
const sprite = new URL('./developer-reference.png', import.meta.url).href;
export const feedbackUrl = `mailto:${EMAIL}?subject=${encodeURIComponent('[Dayflow] 문의 및 피드백')}`;
export const privacyText = '플래너 기록은 기본적으로 이 PC에 저장됩니다. 동기화를 켜면 승인한 내 Dayflow 기기끼리 기록을 공유하며, 기록은 기기에서 암호화된 뒤 서버에 저장됩니다. 암호화 키와 기기 인증 토큰은 이 PC에서 Windows 보호 저장소로 암호화해 보관합니다. 복구 코드는 기록에 접근할 수 있는 비밀이므로 따로 안전하게 보관하세요.';

export function mountDeveloperInfo(container, { version, onFeedback }) {
  container.innerHTML = `
    <section class="dayflow-developer-page" aria-label="개발자 소개">
      <article class="developer-card developer-introduction">
        <div class="developer-art art-person" role="img" aria-label="노트북으로 작업하는 개발자"></div>
        <div class="developer-card-body">
          <h2>개발자</h2>
          <p>Dayflow는 하루의 일정과 업무를 쉽고 직관적으로 관리할 수 있도록<br class="wide-only"> 만든 데스크톱 플래너입니다.</p>
          <p>복잡한 일정 관리보다 오늘 해야 할 일을 빠르게 확인하고<br class="wide-only"> 기록하는 경험에 집중하고 있습니다.</p>
          <div class="developer-badge"><span>Developer</span><i></i><strong>Lebi</strong></div>
        </div>
      </article>
      <article class="developer-card developer-app">
        <div class="developer-art art-planner" role="img" aria-label="분홍색 Dayflow 플래너"></div>
        <div class="developer-card-body">
          <h2>Dayflow</h2>
          <dl class="developer-app-details">
            <div><dt><span class="reference-icon icon-tag" aria-hidden="true"></span>버전</dt><dd data-app-version></dd></div>
            <div><dt><span class="reference-icon icon-monitor" aria-hidden="true"></span>플랫폼</dt><dd>Windows</dd></div>
            <div><dt><span class="reference-icon icon-heart" aria-hidden="true"></span>개발 방향</dt><dd>Simple · Visual · Productive</dd></div>
          </dl>
          <blockquote><span aria-hidden="true">“</span>기록은 가볍게, 하루는 명확하게.</blockquote>
        </div>
      </article>
      <article class="developer-card developer-feedback">
        <div class="developer-art art-envelope" role="img" aria-label="하트가 담긴 파란 편지"></div>
        <div class="developer-card-body">
          <h2>문의 및 피드백</h2>
          <p>Dayflow를 사용하면서 발견한 오류나<br class="wide-only"> 추가되었으면 하는 기능이 있다면 언제든 알려주세요.<br>보내주신 의견은 앞으로의 Dayflow 개선에 참고됩니다.</p>
          <div class="developer-contact">
            <a class="developer-email" href="${feedbackUrl}"><span class="reference-icon icon-email" aria-hidden="true"></span><span>이메일</span><i></i><strong>${EMAIL}</strong></a>
            <button class="developer-feedback-button" type="button"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5h18v14H3z"/><path d="m3 5 9 7 9-7"/></svg>개발자에게 의견 보내기</button>
          </div>
        </div>
      </article>
      <footer class="developer-copyright"><div><span></span>© 2026 Dayflow. All rights reserved.<span></span></div><p>Made with ☕ &amp; 🩵</p></footer>
    </section>`;
  container.querySelector('[data-app-version]').textContent = version;
  for (const art of container.querySelectorAll('.developer-art,.reference-icon')) art.style.backgroundImage = `url("${sprite}")`;
  const send = event => { event.preventDefault(); onFeedback(feedbackUrl); };
  container.querySelector('.developer-feedback-button').addEventListener('click', send);
  container.querySelector('.developer-email').addEventListener('click', send);
  return () => { container.replaceChildren(); };
}
