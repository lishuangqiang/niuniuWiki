(function () {
  let hasInitialized = false;

  const showNiuniuWiki = localStorage.getItem('show-niuniu-wiki') || '';
  const positionStorage = localStorage.getItem('niuniu-wiki-position') || '';
  const [left, top] = positionStorage.split(',');

  const script = document.currentScript.src;
  const origin = new URL(script).origin;
  const link = new URL(script).searchParams.get('link');
  const tools = new URL(script).searchParams.get('tools');

  const makeDraggable = (element, icon) => {
    let isDragging = false;
    let startX, startY, initialX, initialY;
    let animationFrameId = null;
    let dragTimer = null;

    const onMouseDown = (e) => {
      startX = e.clientX;
      startY = e.clientY;
      const rect = element.getBoundingClientRect();
      initialX = rect.left;
      initialY = rect.top;

      // 设置0.5秒的定时器，延迟设置拖拽状态
      dragTimer = setTimeout(() => {
        isDragging = true;
        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
      }, 500);

      document.addEventListener('mouseup', onMouseUp);
    };

    const onMouseMove = (e) => {
      if (!isDragging) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      if (animationFrameId) {
        cancelAnimationFrame(animationFrameId);
      }
      animationFrameId = requestAnimationFrame(() => {
        element.style.left = `${initialX + dx}px`;
        element.style.top = `${initialY + dy}px`;
        localStorage.setItem('niuniu-wiki-position', `${initialX + dx}px,${initialY + dy}px`);
      });
    };

    const onMouseUp = () => {
      // 清除定时器
      if (dragTimer) {
        clearTimeout(dragTimer);
        dragTimer = null;
      }

      // 如果没有进入拖拽状态，则不执行任何操作
      if (!isDragging) {
        document.removeEventListener('mouseup', onMouseUp);
        return;
      }

      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      if (animationFrameId) {
        cancelAnimationFrame(animationFrameId);
        animationFrameId = null;
      }
    };

    icon.addEventListener('click', (e) => {
      if (isDragging) {
        e.stopPropagation();
      } else {
        isDragging = false;
      }
    });

    icon.addEventListener('mousedown', onMouseDown);
  };

  const createWidget = (element) => {
    const widget = document.createElement('div');
    widget.className = 'niuniu-wiki-widget';

    const search_text = document.createElement('div');
    search_text.className = 'niuniu-wiki-search';
    search_text.innerHTML = '开始搜索您的问题';
    widget.appendChild(search_text);
    element.appendChild(widget);

    const ai_text = document.createElement('div');
    ai_text.className = 'niuniu-wiki-text';
    ai_text.innerHTML = 'AI 小助手';
    element.appendChild(ai_text);
  }

  const createLogo = (element) => {
    const icon = document.createElement('div');
    icon.className = 'niuniu-wiki-icon';
    icon.innerHTML = `<img class="niuniu-wiki-icon-mark" src="${new URL('niuniu-avatar.jpg', script).href}" alt="牛牛 Wiki" />`;
    element.appendChild(icon);
    makeDraggable(element, icon);
  }

  const createHideModal = (element) => {
    const hideModal = document.createElement('div');
    hideModal.className = 'niuniu-wiki-hide-modal';

    const hideContainer = document.createElement('div');
    hideContainer.className = 'niuniu-wiki-hide-container';
    hideContainer.innerHTML = `<div class="niuniu-wiki-hide-content">
    <div class="niuniu-wiki-hide-header">
      <svg viewBox="0 0 1024 1024" p-id="6652" width="20" height="20"><path d="M547.13616094 547.13616094H476.86383906V301.0625h70.27232188v246.07366095z m0 175.80133906H476.86383906V652.60491094h70.27232188V722.9375zM512 90.125a421.875 421.875 0 1 0 0 843.75A421.875 421.875 0 0 0 512 90.125z" p-id="6653" fill="#FEA145"></path></svg>
      隐藏挂件
    </div>
  </div>`;

    const hideBody = document.createElement('div');
    hideBody.className = 'niuniu-wiki-hide-body';

    const option1 = document.createElement('div');
    option1.className = 'niuniu-wiki-hide-option';

    const radio1 = document.createElement('input');
    radio1.type = 'radio';
    radio1.name = 'niuniu-wiki-hide-radio';
    radio1.id = 'niuniu-wiki-hide-radio-one';
    radio1.value = 'one';
    radio1.checked = true;
    option1.appendChild(radio1);

    const label1 = document.createElement('label');
    label1.htmlFor = 'niuniu-wiki-hide-radio-one';
    label1.innerHTML = '隐藏本次 <span>将在下次刷新页面时展示并复位挂件</span>';
    option1.appendChild(label1);

    hideBody.appendChild(option1);

    const option2 = document.createElement('div');
    option2.className = 'niuniu-wiki-hide-option';

    const radio2 = document.createElement('input');
    radio2.type = 'radio';
    radio2.name = 'niuniu-wiki-hide-radio';
    radio2.value = 'one-week';
    radio2.id = 'niuniu-wiki-hide-radio-one-week';
    option2.appendChild(radio2);

    const label2 = document.createElement('label');
    label2.htmlFor = 'niuniu-wiki-hide-radio-one-week';
    label2.innerHTML = '隐藏 7 天 <span>7 天后展示并复位挂件</span>';
    option2.appendChild(label2);

    hideBody.appendChild(option2);
    hideContainer.appendChild(hideBody);

    const closeIconBtn = document.createElement('div');
    closeIconBtn.className = 'niuniu-wiki-hide-modal-icon';
    closeIconBtn.innerHTML = '<svg viewBox="0 0 1024 1024" p-id="3836" width="16" height="16"><path d="M758.848 731.456c12.16-12.224 12.16-32 0-44.16L583.616 512l175.232-175.232c12.16-12.16 12.16-32 0-44.16l-27.392-27.456a31.232 31.232 0 0 0-44.16 0L512 440.384 336.768 265.152a31.232 31.232 0 0 0-44.16 0l-27.456 27.392c-12.16 12.224-12.16 32 0 44.16L440.384 512l-175.232 175.232c-12.16 12.16-12.16 32 0 44.16l27.392 27.456c12.224 12.16 32 12.16 44.16 0L512 583.616l175.232 175.232c12.16 12.16 32 12.16 44.16 0l27.456-27.392z" p-id="3837" fill="#21222D"></path></svg>'
    hideContainer.appendChild(closeIconBtn);

    closeIconBtn.addEventListener('click', () => {
      hideModal.classList.remove('active');
    })

    const hideFooter = document.createElement('div');
    hideFooter.className = 'niuniu-wiki-hide-footer';
    hideContainer.appendChild(hideFooter);

    const cancelBtn = document.createElement('button');
    cancelBtn.className = 'niuniu-wiki-hide-cancel-btn';
    cancelBtn.innerHTML = '取消';
    hideFooter.appendChild(cancelBtn);

    const confirmBtn = document.createElement('button');
    confirmBtn.className = 'niuniu-wiki-hide-confirm-btn';
    confirmBtn.innerHTML = '确认';
    hideFooter.appendChild(confirmBtn);

    hideModal.appendChild(hideContainer);
    document.body.appendChild(hideModal);

    cancelBtn.addEventListener('click', () => {
      hideModal.classList.remove('active');
    })

    confirmBtn.addEventListener('click', () => {
      const selectedOption = document.querySelector('input[name="niuniu-wiki-hide-radio"]:checked').value
      if (selectedOption === 'one-week') {
        localStorage.setItem('show-niuniu-wiki', Date.now() + 7 * 24 * 60 * 60 * 1000);
      }
      localStorage.removeItem('niuniu-wiki-position');
      hideModal.classList.remove('active');
      element.style.display = 'none';
    })

    hideModal.addEventListener('click', (e) => {
      if (e.target === hideModal) {
        hideModal.classList.remove('active');
      }
    });

    const closeIcon = document.createElement('div');
    closeIcon.className = 'niuniu-wiki-hide-btn';
    closeIcon.innerHTML = '<svg viewBox="0 0 1024 1024" p-id="6330" id="mx_n_1743146027742" width="16" height="16"><path d="M758.848 731.456c12.16-12.224 12.16-32 0-44.16L583.616 512l175.232-175.232c12.16-12.16 12.16-32 0-44.16l-27.392-27.456a31.232 31.232 0 0 0-44.16 0L512 440.384 336.768 265.152a31.232 31.232 0 0 0-44.16 0l-27.456 27.392c-12.16 12.224-12.16 32 0 44.16L440.384 512l-175.232 175.232c-12.16 12.16-12.16 32 0 44.16l27.392 27.456c12.224 12.16 32 12.16 44.16 0L512 583.616l175.232 175.232c12.16 12.16 32 12.16 44.16 0l27.456-27.392z" p-id="6331" fill="#909095"></path></svg>'
    element.appendChild(closeIcon);

    closeIcon.addEventListener('click', (event) => {
      event.stopPropagation();
      hideModal.classList.add('active');
    })
  }

  const createIframe = (element) => {
    const modal = document.createElement('div');
    modal.className = 'niuniu-wiki-modal';
    const iframeContainer = document.createElement('div');
    iframeContainer.className = 'niuniu-wiki-iframe-container';
    const closeBtn = document.createElement('div');
    closeBtn.className = 'niuniu-wiki-modal-close';
    closeBtn.innerHTML = '<svg viewBox="0 0 1024 1024" p-id="3836" width="16" height="16"><path d="M758.848 731.456c12.16-12.224 12.16-32 0-44.16L583.616 512l175.232-175.232c12.16-12.16 12.16-32 0-44.16l-27.392-27.456a31.232 31.232 0 0 0-44.16 0L512 440.384 336.768 265.152a31.232 31.232 0 0 0-44.16 0l-27.456 27.392c-12.16 12.224-12.16 32 0 44.16L440.384 512l-175.232 175.232c-12.16 12.16-12.16 32 0 44.16l27.392 27.456c12.224 12.16 32 12.16 44.16 0L512 583.616l175.232 175.232c12.16 12.16 32 12.16 44.16 0l27.456-27.392z" p-id="3837" fill="#21222D"></path></svg>'
    iframeContainer.appendChild(closeBtn);
    const iframe = document.createElement('iframe');
    iframe.className = 'niuniu-wiki-iframe';
    iframe.src = `${origin}/plugin/${link}?tools=${tools}`
    element.addEventListener('click', () => {
      iframeContainer.appendChild(iframe);
      modal.classList.add('active');
    });
    closeBtn.addEventListener('click', () => {
      iframeContainer.removeChild(iframe);
      modal.classList.remove('active');
    });
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        modal.classList.remove('active');
      }
    });
    modal.appendChild(iframeContainer);
    document.body.appendChild(modal);
  }

  const init = () => {
    if (hasInitialized) return;
    hasInitialized = true;

    const container = document.createElement('div');
    container.className = 'niuniu-wiki-container';

    if (showNiuniuWiki && Date.now() < showNiuniuWiki) {
      return
    }

    if (link) {
      fetch(`${origin}/share/v1/app/link?link=${link}`).then(res => {
        if (res.ok) {
          res.json().then(data => {
            const position = data?.data?.settings?.position || [4, 24, 24];
            switch (position[0]) {
              case 1:
                container.style.top = position[1] + 'px'
                container.style.left = position[2] + 'px'
                break;
              case 2:
                container.style.top = position[1] + 'px'
                container.style.right = position[2] + 'px'
                break;
              case 3:
                container.style.bottom = position[1] + 'px'
                container.style.left = position[2] + 'px'
                break;
              case 5:
                container.style.top = 'calc(50% - 34px)'
                container.style.left = position[2] + 'px'
                break;
              case 6:
                container.style.top = 'calc(50% - 34px)'
                container.style.right = position[2] + 'px'
                break;
              default:
                container.style.bottom = position[1] + 'px'
                container.style.right = position[2] + 'px'
            }
            if (positionStorage) {
              container.style.left = left
              container.style.top = top
            }
            container.style.display = 'block';
          })
        }
      })
    }
    createWidget(container);
    createLogo(container);
    createHideModal(container);
    createIframe(container);
    document.body.appendChild(container);
  }

  if (document.readyState === 'complete') init();
  else if (document.readyState === 'interactive') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  }
  window.addEventListener('load', init, { once: true });
})();
