import { ConstsHomePageSetting } from '@/request/types';
import { getBasePath } from '@/utils/getBasePath';

export const INIT_DOC_DATA = [
  {
    type: 2,
    emoji: '👋',
    name: '欢迎来到牛牛 Wiki',
    summary:
      '从创建第一篇文档开始，把零散信息沉淀为可检索、可追溯、可持续生长的团队知识。',
    content:
      '<h1>欢迎来到牛牛 Wiki</h1><p>牛牛 Wiki 将文档管理、智能检索与 AI 问答放在同一个知识空间中。</p><h2>从这里开始</h2><ol><li><p>在管理端创建或导入文档，并通过目录组织内容。</p></li><li><p>在系统设置中接入对话、向量与重排序模型。</p></li><li><p>发布知识库，等待文档完成索引。</p></li><li><p>打开应用端，通过搜索或 AI 问答验证知识是否可被准确找到。</p></li></ol><blockquote><p>建议先建立清晰的标题层级，再为关键文档补充摘要。结构越清楚，检索与阅读体验越好。</p></blockquote>',
  },
  {
    type: 2,
    emoji: '✨',
    name: '让 AI 更懂你的知识',
    summary:
      '通过稳定的内容发布、合理的文档边界和清晰的问题表达，提升知识检索与问答质量。',
    content:
      '<h1>让 AI 更懂你的知识</h1><p>牛牛 Wiki 会在文档发布后建立检索索引，并在问答时召回与问题相关的内容。</p><h2>内容建议</h2><ul><li><p>一篇文档聚焦一个主题，避免堆叠无关内容。</p></li><li><p>使用明确标题表达“这段内容解决什么问题”。</p></li><li><p>关键结论写在前面，并补充必要上下文。</p></li><li><p>内容更新后及时重新发布，确保用户读取最新版本。</p></li></ul><h2>提问建议</h2><p>尽量写清对象、场景和目标。例如，将“怎么配置”改为“在本地部署环境中如何配置对话模型”。</p>',
  },
  {
    type: 2,
    emoji: '🧭',
    name: '知识库运营清单',
    summary: '用一份轻量清单持续维护内容质量、检索效果与用户反馈。',
    content:
      '<h1>知识库运营清单</h1><ul data-type="taskList"><li data-checked="false"><label><input type="checkbox"><span></span></label><div><p>清理过期内容与失效链接</p></div></li><li data-checked="false"><label><input type="checkbox"><span></span></label><div><p>检查未完成索引的文档</p></div></li><li data-checked="false"><label><input type="checkbox"><span></span></label><div><p>复盘高频问题与无答案问题</p></div></li><li data-checked="false"><label><input type="checkbox"><span></span></label><div><p>根据用户反馈补充示例和边界说明</p></div></li></ul>',
  },
] as const;

export const INIT_LADING_DATA = {
  title: '牛牛 Wiki',
  desc: '让知识被看见，也被真正理解。',
  theme_mode: 'light',
  home_page_setting:
    ConstsHomePageSetting.HomePageSettingCustom as ConstsHomePageSetting,
  icon: getBasePath('/niuniu-avatar.jpg'),
  btns: [],
  web_app_custom_style: {
    allow_theme_switching: true,
    header_search_placeholder: '搜索文档，或向 AI 提问',
    show_brand_info: true,
    footer_show_intro: true,
    social_media_accounts: [],
  },
  footer_settings: {
    footer_style: 'complex',
    corp_name: '',
    icp: '',
    brand_name: '牛牛 Wiki',
    brand_desc:
      '一个安静、清晰的 AI 知识空间，帮助团队组织文档、发现信息并获得有依据的回答。',
    brand_logo: getBasePath('/niuniu-avatar.jpg'),
    brand_groups: [],
  },
  web_app_landing_configs: [
    {
      type: 'banner',
      banner_config: {
        title: '让知识被看见，也被真正理解。',
        title_color: '#1D1D1F',
        title_font_size: 64,
        subtitle:
          '牛牛 Wiki 将文档、智能检索与 AI 问答融入一个专注的空间，让每一次查询都更快抵达答案。',
        placeholder: '输入你的问题',
        subtitle_color: '#6E6E73',
        subtitle_font_size: 17,
        bg_url: '',
        hot_search: ['如何创建第一篇文档？', '怎样提升 AI 问答效果？', '如何发布知识库？'],
        btns: [
          {
            id: 'niuniu-start',
            text: '开始探索',
            type: 'contained',
            href: '/node',
          },
        ],
      },
      node_ids: [],
      nodes: null,
    },
    {
      type: 'basic_doc',
      basic_doc_config: {
        title: '从这里开始',
        title_color: '#1D1D1F',
        bg_color: '#F5F5F7',
      },
      node_ids: [],
    },
    {
      type: 'feature',
      feature_config: {
        title: '专注知识本身',
        title_color: '#1D1D1F',
        bg_color: '#FFFFFF',
        list: [
          { id: 'organize', name: '清晰组织', desc: '用目录、版本和权限维护可信内容。' },
          { id: 'retrieve', name: '智能检索', desc: '从海量文档中快速定位相关信息。' },
          { id: 'answer', name: '有据回答', desc: '让 AI 基于知识库内容生成答案与引用。' },
        ],
      },
      node_ids: [],
      nodes: null,
    },
    {
      type: 'faq',
      faq_config: {
        title: '常见问题',
        title_color: '#1D1D1F',
        bg_color: '#F5F5F7',
        list: [
          { id: 'faq-model', question: '如何接入 AI 模型？', link: '' },
          { id: 'faq-publish', question: '为什么更新后需要重新发布？', link: '' },
          { id: 'faq-retrieve', question: '怎样提高知识检索命中率？', link: '' },
        ],
      },
      node_ids: [],
      nodes: null,
    },
  ],
};
