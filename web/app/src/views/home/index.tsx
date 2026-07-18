'use client';

import { Banner } from '@niuniu-wiki/ui';
import AutoAwesomeRounded from '@mui/icons-material/AutoAwesomeRounded';
import AddCircleOutlineRounded from '@mui/icons-material/AddCircleOutlineRounded';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import AttachFileRounded from '@mui/icons-material/AttachFileRounded';
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded';
import DescriptionRounded from '@mui/icons-material/DescriptionRounded';
import FolderRounded from '@mui/icons-material/FolderRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import SendRounded from '@mui/icons-material/SendRounded';
import {
  alpha,
  Box,
  Button,
  IconButton,
  InputBase,
  Stack,
  Typography,
} from '@mui/material';
import dynamic from 'next/dynamic';
import Link from 'next/link';
import { DomainRecommendNodeListResp } from '@/request/types';
import { convertToTree } from '@/utils/tree';
import { useStore } from '@/provider';
import { ITreeItem } from '@/assets/type';
import { useMemo, useRef, useState } from 'react';
import { useEffect } from 'react';
import {
  CONVERSATION_HISTORY_EVENT,
  ConversationHistoryEntry,
  getConversationHistoryScope,
  readConversationHistory,
} from '@/utils/conversationHistory';

import { useBasePath } from '@/hooks';
import { getImagePath } from '@/utils/getImagePath';
const handleFaqProps = (config: any = {}) => {
  return {
    title: config.title || '链接组',
    items:
      config.list?.map((item: any) => ({
        question: item.question,
        url: item.link,
      })) || [],
  };
};

const handleBasicDocProps = (
  config: any = {},
  docs: DomainRecommendNodeListResp[],
  basePath: string,
) => {
  return {
    title: config.title || '文档摘要卡片',
    basePath,
    items:
      docs?.map(item => ({
        ...item,
        summary: item.summary || '暂无摘要',
      })) || [],
  };
};

const handleDirDocProps = (
  config: any = {},
  docs: DomainRecommendNodeListResp[],
  basePath: string,
) => {
  return {
    title: config.title || '文件夹卡片',
    basePath,
    items:
      docs?.map(item => ({
        id: item.id,
        name: item.name,
        ...item,
        recommend_nodes: [...(item.recommend_nodes || [])].sort(
          (a, b) => (a.position ?? 0) - (b.position ?? 0),
        ),
      })) || [],
  };
};

const handleNavDocProps = (
  config: any = {},
  docs: DomainRecommendNodeListResp[],
  basePath: string,
) => {
  return {
    title: config.title || '目录卡片',
    basePath,
    items:
      docs?.map(item => ({
        id: item.id,
        name: item.name,
        ...item,
        // @ts-ignore
        list: convertToTree(item.recommend_nodes || []),
      })) || [],
  };
};

const handleSimpleDocProps = (
  config: any = {},
  docs: DomainRecommendNodeListResp[],
  basePath: string,
) => {
  return {
    title: config.title || '简易文档卡片',
    basePath,
    items:
      docs?.map(item => ({
        ...item,
      })) || [],
  };
};

const handleCarouselProps = (config: any = {}, basePath: string) => {
  return {
    title: config.title || '轮播图',
    items:
      config.list?.map((item: any) => ({
        id: item.id,
        title: item.title,
        url: getImagePath(item.url, basePath),
        desc: item.desc,
      })) || [],
  };
};

const handleBannerProps = (config: any = {}, basePath: string) => {
  return {
    title: {
      text: config.title,
    },
    subtitle: {
      text: config.subtitle,
    },
    bg_url: getImagePath(config.bg_url, basePath),
    search: {
      placeholder: config.placeholder,
      hot: config.hot_search,
    },
  };
};

const handleTextProps = (config: any = {}) => {
  return {
    title: config.title || '标题',
  };
};

const handleCaseProps = (config: any = {}) => {
  return {
    title: config.title || '案例',
    items: config.list || [],
  };
};

const handleMetricsProps = (config: any = {}) => {
  return {
    title: config.title || '指标',
    items: config.list || [],
  };
};

const handleFeatureProps = (config: any = {}) => {
  return {
    title: config.title || '产品特性',
    items: config.list || [],
  };
};

const handleImgTextProps = (config: any = {}, basePath: string) => {
  return {
    title: config.title || '左图右字',
    item: {
      ...config.item,
      url: getImagePath(config.item?.url, basePath),
    },
    direction: 'row',
  };
};

const handleTextImgProps = (config: any = {}, basePath: string) => {
  return {
    title: config.title || '右图左字',
    item: {
      ...config.item,
      url: getImagePath(config.item?.url, basePath),
    },
    direction: 'row-reverse',
  };
};

const handleCommentProps = (config: any = {}, basePath: string) => {
  return {
    title: config.title || '评论卡片',
    items:
      config.list?.map((item: any) => ({
        ...item,
        avatar: getImagePath(item.avatar, basePath),
      })) || [],
  };
};

const handleBlockGridProps = (config: any = {}, basePath: string) => {
  return {
    title: config.title || '区块网格',
    basePath,
    items:
      config.list?.map((item: any) => ({
        ...item,
        url: getImagePath(item.url, basePath),
      })) || [],
  };
};

const handleQuestionProps = (config: any = {}) => {
  return {
    title: config.title || '常见问题',
    items: config.list || [],
  };
};

const componentMap = {
  banner: Banner,
  basic_doc: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.BasicDoc)),
  dir_doc: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.DirDoc)),
  simple_doc: dynamic(() =>
    import('@niuniu-wiki/ui').then(mod => mod.SimpleDoc),
  ),
  carousel: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.Carousel)),
  faq: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.Faq)),
  text: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.Text)),
  nav_doc: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.NavDoc)),
  case: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.Case)),
  metrics: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.Metrics)),
  feature: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.Feature)),
  text_img: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.ImgText)),
  img_text: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.ImgText)),
  comment: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.Comment)),
  block_grid: dynamic(() =>
    import('@niuniu-wiki/ui').then(mod => mod.BlockGrid),
  ),
  question: dynamic(() => import('@niuniu-wiki/ui').then(mod => mod.Question)),
} as const;

const flattenNodes = (items: ITreeItem[] = []): ITreeItem[] =>
  items.flatMap(item => [item, ...flattenNodes(item.children || [])]);

const formatHistoryTime = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '刚刚更新';
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  return sameDay
    ? `今天 ${date.toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
      })}`
    : date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
};

const DefaultLanding = ({
  tree,
  basePath,
  onSearch,
  onAttach,
  histories,
  onHistoryOpen,
}: {
  tree: ITreeItem[];
  basePath: string;
  onSearch: (value: string, type?: 'chat' | 'search') => void;
  onAttach: (files: File[]) => void;
  histories: ConversationHistoryEntry[];
  onHistoryOpen: (history: ConversationHistoryEntry) => void;
}) => {
  const [query, setQuery] = useState('');
  const [showAllHistory, setShowAllHistory] = useState(false);
  const [showFloatingComposer, setShowFloatingComposer] = useState(false);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const waterfallRef = useRef<HTMLDivElement>(null);
  const allNodes = useMemo(() => flattenNodes(tree), [tree]);
  const documents = useMemo(
    () => allNodes.filter(item => item.type === 2),
    [allNodes],
  );
  const folderCount = allNodes.filter(item => item.type === 1).length;
  const coverLabels = [
    ['ASK.', 'FIND.', 'KNOW.'],
    ['IDEAS', 'BECOME', 'CLEAR.'],
    ['READ.', 'THINK.', 'BUILD.'],
    ['SEARCH', 'CONNECT', 'ANSWER.'],
    ['LEARN.', 'SHARE.', 'GROW.'],
    ['MAKE', 'KNOWLEDGE', 'USEFUL.'],
  ];
  const featureCards = [
    {
      title: '向知识库提问',
      desc: '用自然语言描述问题，基于现有文档生成带上下文的回答。',
      prompt: '这份知识库包含什么？',
      labels: ['ASK.', 'AI', 'ANSWER.'],
    },
    {
      title: '精准搜索原文',
      desc: '从相关文档片段出发，快速回到可信的原始内容。',
      prompt: '帮我快速找到最相关的文档',
      labels: ['SEARCH.', 'TRACE.', 'FIND.'],
    },
    {
      title: '查看最近更新',
      desc: '让 AI 帮你梳理最近发布和发生变化的重要知识。',
      prompt: '总结最近更新的内容',
      labels: ['NEW.', 'READ.', 'GROW.'],
    },
    {
      title: '提炼技术亮点',
      desc: '从文档中找到值得深入学习和写进项目经历的技术实现。',
      prompt: '从知识库中提炼有技术深度的项目亮点',
      labels: ['TRACE.', 'TECH.', 'DEPTH.'],
    },
    {
      title: '生成学习路线',
      desc: '根据现有知识内容组织一条由浅入深、可以执行的学习路径。',
      prompt: '根据知识库内容为我生成一条学习路线',
      labels: ['LEARN.', 'STEP.', 'GROW.'],
    },
    {
      title: '对比不同方案',
      desc: '把散落在文档中的方案放到同一维度下进行结构化比较。',
      prompt: '对比知识库中不同项目和技术方案的优缺点',
      labels: ['READ.', 'MATCH.', 'DECIDE.'],
    },
    {
      title: '查找原始出处',
      desc: '从回答直接回到原文、项目地址和最可信的上下文。',
      prompt: '帮我找到知识库里项目的原始链接和出处',
      labels: ['SOURCE.', 'LINK.', 'VERIFY.'],
    },
    {
      title: '整理项目表达',
      desc: '把已有资料整理为更清晰的功能说明、技术方案与成果表达。',
      prompt: '把知识库中的项目整理成有技术深度的项目表达',
      labels: ['MAKE.', 'CLEAR.', 'USEFUL.'],
    },
    {
      title: '总结核心观点',
      desc: '压缩长文档中的关键结论，快速形成可以复用的知识摘要。',
      prompt: '总结知识库中最重要的核心观点',
      labels: ['READ.', 'CUT.', 'KEEP.'],
    },
    {
      title: '提取技术栈',
      desc: '识别项目涉及的框架、中间件、模型能力和工程技术选型。',
      prompt: '提取知识库项目涉及的技术栈并分类说明',
      labels: ['STACK.', 'MAP.', 'BUILD.'],
    },
    {
      title: '挖掘边缘问题',
      desc: '寻找主流程之外容易被忽略的一致性、容错和性能问题。',
      prompt: '挖掘知识库项目中值得解决的边缘问题',
      labels: ['EDGE.', 'CHECK.', 'SOLVE.'],
    },
    {
      title: '生成实践清单',
      desc: '把知识内容转化为可以逐项完成的编码任务与验证目标。',
      prompt: '根据知识库为我生成项目实践任务清单',
      labels: ['PLAN.', 'CODE.', 'SHIP.'],
    },
    {
      title: '构建问题清单',
      desc: '围绕当前资料生成一组循序渐进、能够检验理解的问题。',
      prompt: '根据知识库内容生成一份由浅入深的问题清单',
      labels: ['ASK.', 'TEST.', 'LEARN.'],
    },
    {
      title: '形成复习卡片',
      desc: '把概念、方案和结论整理成适合反复回顾的知识卡片。',
      prompt: '把知识库内容整理成便于复习的知识卡片',
      labels: ['NOTE.', 'CARD.', 'RECALL.'],
    },
    {
      title: '发现关联内容',
      desc: '串联分散在不同文档和段落中的项目、技术与应用场景。',
      prompt: '帮我发现知识库中彼此关联的内容',
      labels: ['FIND.', 'LINK.', 'EXPLORE.'],
    },
    {
      title: '解释复杂概念',
      desc: '基于原始资料，用更容易理解的语言拆解复杂技术概念。',
      prompt: '挑选知识库中的复杂概念并用通俗语言解释',
      labels: ['DEEP.', 'SIMPLE.', 'CLEAR.'],
    },
    {
      title: '生成演示大纲',
      desc: '将知识内容组织为适合分享、答辩或项目演示的叙事结构。',
      prompt: '根据知识库内容生成一份项目演示大纲',
      labels: ['TELL.', 'SHOW.', 'SHARE.'],
    },
    {
      title: '整理开源项目',
      desc: '汇总资料中的项目地址、核心能力、技术特点与适用场景。',
      prompt: '整理知识库中提到的全部开源项目',
      labels: ['OPEN.', 'CODE.', 'DISCOVER.'],
    },
    {
      title: '输出面试题',
      desc: '从技术实现中抽取值得追问的工程问题和参考回答。',
      prompt: '根据知识库项目生成有技术深度的面试题',
      labels: ['ASK.', 'WHY.', 'ANSWER.'],
    },
    {
      title: '制作知识地图',
      desc: '把文档、概念、项目和技术依赖组织成清晰的知识结构。',
      prompt: '根据知识库内容制作一份知识地图',
      labels: ['MAP.', 'CONNECT.', 'KNOW.'],
    },
  ];
  const visibleHistories = showAllHistory ? histories : histories.slice(0, 4);
  const historyCovers = [
    {
      background: 'linear-gradient(135deg, #11131a 0%, #2b3cff 100%)',
      accent: '#c8ff39',
      words: ['ASK.', 'THINK.', 'RETURN.'],
    },
    {
      background: 'linear-gradient(135deg, #5119a7 0%, #ff5470 100%)',
      accent: '#ffe766',
      words: ['IDEAS.', 'STAY.', 'CLEAR.'],
    },
    {
      background: 'linear-gradient(135deg, #043d3a 0%, #00a982 100%)',
      accent: '#ffb3c4',
      words: ['FIND.', 'TRACE.', 'KNOW.'],
    },
    {
      background: 'linear-gradient(135deg, #8f160e 0%, #ff4d16 100%)',
      accent: '#bde5ff',
      words: ['READ.', 'RECALL.', 'GROW.'],
    },
  ];

  const submit = (value = query) => {
    const normalized = value.trim();
    if (normalized) onSearch(normalized, 'chat');
    else onSearch('这份知识库包含什么？', 'chat');
  };

  useEffect(() => {
    const target = waterfallRef.current;
    if (!target) return;
    const observer = new IntersectionObserver(
      ([entry]) => setShowFloatingComposer(entry.isIntersecting),
      {
        threshold: 0,
        rootMargin: '-72px 0px -12% 0px',
      },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, []);

  return (
    <Box
      sx={{
        overflow: 'hidden',
        bgcolor: 'transparent',
        color: '#080808',
        fontFamily:
          'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif',
      }}
    >
      <Box
        component='section'
        sx={theme => ({
          position: 'relative',
          minHeight: { xs: 590, md: 500 },
          display: 'flex',
          alignItems: 'flex-start',
          px: { xs: 2.5, sm: 5 },
          pt: { xs: 7, md: 10 },
          pb: { xs: 8, md: 7 },
          isolation: 'isolate',
          backgroundColor: 'transparent',
          backgroundImage: 'none',
          '&::before': {
            display: 'none',
          },
          '&::after': {
            display: 'none',
          },
        })}
      >
        <Stack
          alignItems='center'
          textAlign='center'
          sx={{ width: '100%', maxWidth: 1176, mx: 'auto' }}
        >
          <Stack
            className='niu-rise'
            direction='row'
            alignItems='center'
            gap={0.75}
            sx={theme => ({
              display: 'none',
            })}
          >
            <AutoAwesomeRounded sx={{ fontSize: 15 }} />
            搜索与 AI，从同一个问题开始
          </Stack>

          <Typography
            component='h1'
            className='niu-rise'
            sx={{
              maxWidth: 900,
              fontFamily:
                'Outfit, "Avenir Next", var(--font-gilory), "PingFang SC", system-ui, sans-serif',
              fontSize: { xs: 28, sm: 32, md: 36 },
              lineHeight: 1.25,
              fontWeight: 600,
              letterSpacing: '-0.025em',
              color: '#080808',
              animationDelay: '80ms',
            }}
          >
            说出你的问题，牛牛 Wiki 和你一起找到答案
          </Typography>

          <Typography
            className='niu-rise'
            sx={{
              mt: 1.5,
              maxWidth: 680,
              color: '#777b84',
              fontSize: { xs: 13, md: 14 },
              lineHeight: 1.7,
              letterSpacing: 0,
              animationDelay: '150ms',
            }}
          >
            让知识库与你一起思考，从原文检索到答案组织，一次完成。
          </Typography>

          <Box
            className='banner-search-box niu-rise'
            sx={theme => ({
              width: '100%',
              maxWidth: 820,
              height: { xs: 190, sm: 166, md: 150 },
              mt: { xs: 4, md: 4.5 },
              p: 1.5,
              borderRadius: '12px',
              bgcolor: '#fff',
              border: `0.5px solid ${alpha('#111c30', 0.14)}`,
              boxShadow: '0 4px 10px rgba(0, 0, 0, .10)',
              animationDelay: '220ms',
              transition:
                'box-shadow .2s ease, border-color .2s ease, transform .2s ease',
              '&:focus-within': {
                borderColor: alpha('#111c30', 0.32),
                boxShadow:
                  '0 7px 20px rgba(0, 0, 0, .12), 0 0 0 3px rgba(17,28,48,.045)',
              },
            })}
          >
            <Stack sx={{ height: '100%' }}>
              <Stack direction='row' alignItems='center' gap={0.8}>
                <Box
                  sx={{
                    width: 26,
                    height: 26,
                    display: 'grid',
                    placeItems: 'center',
                    color: '#fff',
                    bgcolor: '#111',
                    borderRadius: '50%',
                  }}
                >
                  <AutoAwesomeRounded sx={{ fontSize: 14 }} />
                </Box>
                <Box
                  sx={{
                    px: 1,
                    py: 0.45,
                    bgcolor: '#f5f5f5',
                    borderRadius: '999px',
                    color: '#666a72',
                    fontSize: 12,
                    fontWeight: 500,
                  }}
                >
                  知识检索与 AI 问答
                </Box>
              </Stack>
              <InputBase
                fullWidth
                multiline
                maxRows={3}
                value={query}
                onChange={event => setQuery(event.target.value)}
                onKeyDown={event => {
                  if (
                    event.key === 'Enter' &&
                    !event.shiftKey &&
                    !event.nativeEvent.isComposing
                  ) {
                    event.preventDefault();
                    submit();
                  }
                }}
                inputProps={{ 'aria-label': '搜索知识库或向 AI 提问' }}
                placeholder='输入问题、关键词或文档主题，让知识库帮你实现。'
                sx={{
                  flex: 1,
                  alignItems: 'flex-start',
                  pt: 1.4,
                  px: 0.4,
                  fontSize: { xs: 14, md: 14.5 },
                  lineHeight: 1.6,
                  color: '#111',
                  '& textarea::placeholder': {
                    color: '#9a9ca2',
                    opacity: 1,
                  },
                }}
              />
              <Stack
                direction='row'
                alignItems='center'
                justifyContent='space-between'
              >
                <Stack direction='row' alignItems='center' gap={0.5}>
                  <input
                    ref={imageInputRef}
                    type='file'
                    accept='.jpg,.jpeg,.png,.gif,.webp'
                    multiple
                    hidden
                    onChange={event => {
                      onAttach(Array.from(event.target.files || []));
                      event.target.value = '';
                    }}
                  />
                  <input
                    ref={fileInputRef}
                    type='file'
                    accept='.txt,.md,.csv,.json,.html,.xml,.yaml,.yml,.log'
                    multiple
                    hidden
                    onChange={event => {
                      onAttach(Array.from(event.target.files || []));
                      event.target.value = '';
                    }}
                  />
                  <IconButton
                    aria-label='上传图片'
                    size='small'
                    onClick={() => imageInputRef.current?.click()}
                    sx={{ color: '#777b84' }}
                  >
                    <AddCircleOutlineRounded sx={{ fontSize: 19 }} />
                  </IconButton>
                  <IconButton
                    aria-label='上传文件'
                    size='small'
                    onClick={() => fileInputRef.current?.click()}
                    sx={{ color: '#777b84' }}
                  >
                    <AttachFileRounded sx={{ fontSize: 18 }} />
                  </IconButton>
                </Stack>
                <Stack direction='row' alignItems='center' gap={1}>
                  <Button
                    variant='outlined'
                    size='small'
                    startIcon={<AutoAwesomeRounded sx={{ fontSize: 14 }} />}
                    sx={{
                      minHeight: 34,
                      px: 1.5,
                      color: '#666a72',
                      borderColor: '#e1e2e5',
                      fontSize: 12,
                      '&:hover': {
                        borderColor: '#c8c9cc',
                        bgcolor: '#fafafa',
                        boxShadow: 'none',
                      },
                    }}
                  >
                    深度思考
                  </Button>
                  <IconButton
                    aria-label='开始智能问答'
                    onClick={() => submit()}
                    sx={{
                      width: 40,
                      height: 40,
                      color: '#fff',
                      bgcolor: '#111',
                      '&:hover': { bgcolor: '#303030' },
                    }}
                  >
                    <SendRounded sx={{ fontSize: 19 }} />
                  </IconButton>
                </Stack>
              </Stack>
            </Stack>
          </Box>

          <Stack
            className='niu-rise'
            direction='row'
            alignItems='center'
            justifyContent='center'
            flexWrap='wrap'
            gap={1}
            sx={{ display: 'none' }}
          >
            {[
              '这份知识库包含什么？',
              '帮我快速找到入门文档',
              '总结最近更新的内容',
            ].map(item => (
              <Button
                key={item}
                variant='text'
                size='small'
                onClick={() => submit(item)}
                sx={{
                  minHeight: 30,
                  px: 1.25,
                  color: 'text.secondary',
                  fontSize: 12.5,
                }}
              >
                {item}
              </Button>
            ))}
          </Stack>
        </Stack>
      </Box>

      <Box
        component='section'
        sx={{
          px: { xs: 2.5, sm: 5 },
          pt: { xs: 6, md: 8 },
          pb: { xs: 10, md: 12 },
          bgcolor: 'transparent',
          backgroundImage:
            'linear-gradient(180deg, rgba(255,255,255,0) 0px, rgba(255,255,255,.78) 96px, #fff 190px, #fff 100%)',
        }}
      >
        <Box sx={{ maxWidth: 1176, mx: 'auto' }}>
          {histories.length > 0 && (
            <Box component='section' aria-labelledby='history-heading'>
              <Stack
                direction='row'
                alignItems='center'
                justifyContent='space-between'
                sx={{ mb: 2.5 }}
              >
                <Typography
                  id='history-heading'
                  component='h2'
                  sx={{
                    fontFamily:
                      'Outfit, "Avenir Next", var(--font-gilory), "PingFang SC", system-ui, sans-serif',
                    fontSize: { xs: 22, md: 24 },
                    lineHeight: 1.3,
                    fontWeight: 650,
                    letterSpacing: '-0.02em',
                    color: '#0a0a0a',
                  }}
                >
                  历史消息
                </Typography>
                {histories.length > 4 && (
                  <Button
                    variant='text'
                    endIcon={<ArrowForwardRounded sx={{ fontSize: 16 }} />}
                    onClick={() => setShowAllHistory(value => !value)}
                    sx={{
                      px: 0.5,
                      color: '#777b84',
                      fontSize: 12.5,
                      '&:hover': { bgcolor: 'transparent', color: '#111' },
                    }}
                  >
                    {showAllHistory ? '收起' : '查看全部'}
                  </Button>
                )}
              </Stack>

              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns: {
                    xs: '1fr',
                    sm: 'repeat(2, minmax(0, 1fr))',
                    lg: 'repeat(4, minmax(0, 1fr))',
                  },
                  gap: { xs: 2, md: 2.25 },
                }}
              >
                {visibleHistories.map((history, index) => {
                  const cover = historyCovers[index % historyCovers.length];
                  return (
                    <Box
                      component='button'
                      type='button'
                      key={history.id}
                      onClick={() => onHistoryOpen(history)}
                      aria-label={`继续对话：${history.title}`}
                      sx={{
                        m: 0,
                        p: 0,
                        border: 0,
                        minWidth: 0,
                        bgcolor: 'transparent',
                        color: '#111',
                        font: 'inherit',
                        textAlign: 'left',
                        cursor: 'pointer',
                        '&:hover .history-cover': {
                          transform: 'translateY(-4px)',
                          boxShadow: '0 18px 38px rgba(17, 28, 48, .18)',
                        },
                        '&:focus-visible': {
                          outline: '2px solid #111',
                          outlineOffset: 5,
                          borderRadius: '14px',
                        },
                      }}
                    >
                      <Box
                        className='history-cover'
                        sx={{
                          position: 'relative',
                          height: 156,
                          overflow: 'hidden',
                          borderRadius: '14px',
                          p: 2.25,
                          color: '#fff',
                          background: cover.background,
                          boxShadow: '0 10px 24px rgba(17, 28, 48, .12)',
                          transition:
                            'transform .24s ease, box-shadow .24s ease',
                          '&::before': {
                            content: '""',
                            position: 'absolute',
                            inset: 0,
                            opacity: 0.22,
                            backgroundImage:
                              'linear-gradient(rgba(255,255,255,.28) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.28) 1px, transparent 1px)',
                            backgroundSize: '24px 24px',
                          },
                          '&::after': {
                            content: '""',
                            position: 'absolute',
                            width: 128,
                            height: 128,
                            right: -34,
                            bottom: -46,
                            borderRadius: index % 2 === 0 ? '50%' : '28px',
                            bgcolor: cover.accent,
                            transform: `rotate(${index % 2 === 0 ? 0 : 18}deg)`,
                          },
                        }}
                      >
                        <Stack
                          sx={{
                            position: 'relative',
                            zIndex: 1,
                            fontFamily:
                              'Outfit, "Arial Black", var(--font-gilory), sans-serif',
                            fontSize: 26,
                            lineHeight: 0.88,
                            fontWeight: 850,
                            letterSpacing: '-0.055em',
                          }}
                        >
                          {cover.words.map((word, wordIndex) => (
                            <Box
                              component='span'
                              key={word}
                              sx={{
                                color: wordIndex === 2 ? cover.accent : '#fff',
                              }}
                            >
                              {word}
                            </Box>
                          ))}
                        </Stack>
                        <Stack
                          direction='row'
                          alignItems='center'
                          gap={0.6}
                          sx={{
                            position: 'absolute',
                            left: 18,
                            bottom: 16,
                            zIndex: 1,
                            fontSize: 11,
                            color: 'rgba(255,255,255,.78)',
                          }}
                        >
                          <ChatBubbleOutlineRounded sx={{ fontSize: 14 }} />
                          {history.messageCount} 条问答
                        </Stack>
                      </Box>
                      <Typography
                        noWrap
                        sx={{
                          mt: 1.45,
                          px: 0.4,
                          fontSize: 15,
                          fontWeight: 650,
                        }}
                      >
                        {history.title}
                      </Typography>
                      <Typography
                        noWrap
                        sx={{
                          mt: 0.45,
                          px: 0.4,
                          color: '#8a8d93',
                          fontSize: 12,
                        }}
                      >
                        {history.preview} ·{' '}
                        {formatHistoryTime(history.updatedAt)}
                      </Typography>
                    </Box>
                  );
                })}
              </Box>
            </Box>
          )}

          <Stack
            direction={{ xs: 'column', md: 'row' }}
            alignItems={{ xs: 'flex-start', md: 'center' }}
            justifyContent='space-between'
            gap={2}
            sx={{
              mt: histories.length > 0 ? { xs: 7, md: 9 } : 0,
              mb: { xs: 3, md: 3.5 },
            }}
          >
            <Box>
              <Typography
                sx={{
                  display: 'none',
                }}
              >
                从这里开始
              </Typography>
              <Typography
                component='h2'
                sx={{
                  fontFamily:
                    'Outfit, "Avenir Next", var(--font-gilory), "PingFang SC", system-ui, sans-serif',
                  fontSize: { xs: 23, md: 25 },
                  lineHeight: 1.3,
                  fontWeight: 600,
                  letterSpacing: '-0.02em',
                  color: '#0a0a0a',
                }}
              >
                探索知识库
              </Typography>
            </Box>
            {allNodes.length > 0 && (
              <Typography sx={{ color: '#8a8d93', fontSize: 12.5 }}>
                {documents.length > 0
                  ? `${allNodes.filter(item => item.type === 2).length} 篇文档`
                  : '知识内容'}
                {folderCount > 0 ? ` · ${folderCount} 个目录` : ''}
              </Typography>
            )}
          </Stack>

          {false && (
            <Box
              sx={{
                display: 'none',
                gridTemplateColumns: {
                  xs: '1fr',
                  md: 'repeat(2, minmax(0, 1fr))',
                  lg: 'repeat(4, minmax(0, 1fr))',
                },
                gap: { xs: 2, md: 2.25 },
              }}
            >
              {documents.length > 0 ? (
                <>
                  {documents.map((item, index) => (
                    <Box
                      component={Link}
                      href={`${basePath}/node/${item.id}`}
                      key={item.id}
                      sx={theme => ({
                        position: 'relative',
                        minHeight: 360,
                        p: 1,
                        display: 'flex',
                        flexDirection: 'column',
                        borderRadius: '16px',
                        overflow: 'hidden',
                        bgcolor: '#fff',
                        color: '#111',
                        border: `1px solid ${alpha('#111c30', 0.09)}`,
                        boxShadow: '0 12px 34px rgba(17, 28, 48, .08)',
                        transition: 'transform .25s ease, box-shadow .25s ease',
                        '&:hover': {
                          transform: 'translateY(-4px)',
                          boxShadow: '0 18px 46px rgba(17, 28, 48, .13)',
                          '& .doc-arrow': { transform: 'translateX(4px)' },
                        },
                      })}
                    >
                      <Box
                        sx={theme => ({
                          position: 'relative',
                          width: '100%',
                          height: 186,
                          flexShrink: 0,
                          overflow: 'hidden',
                          borderRadius: '12px',
                          bgcolor: index % 2 === 0 ? '#f3f0e8' : '#f5f2ea',
                          color: '#090909',
                          p: 2,
                          backgroundImage: `linear-gradient(${alpha('#111', 0.045)} 1px, transparent 1px), linear-gradient(90deg, ${alpha('#111', 0.045)} 1px, transparent 1px)`,
                          backgroundSize: '22px 22px',
                          '&::before': {
                            content: '\"\"',
                            position: 'absolute',
                            width: 130,
                            height: 130,
                            right: -18,
                            bottom: -24,
                            borderRadius: index % 3 === 1 ? '50%' : '4px',
                            bgcolor: index % 2 === 0 ? '#ef1d1d' : '#111',
                            transform:
                              index % 3 === 2
                                ? 'rotate(12deg)'
                                : 'rotate(0deg)',
                          },
                          '&::after': {
                            content: '\"\"',
                            position: 'absolute',
                            width: 66,
                            height: 118,
                            right: 30,
                            bottom: -12,
                            border: '1px solid rgba(255,255,255,.72)',
                            borderRadius: index % 2 === 0 ? '18px' : '4px',
                            transform: 'rotate(-8deg)',
                          },
                        })}
                      >
                        <Stack
                          sx={{
                            position: 'relative',
                            zIndex: 1,
                            width: '72%',
                            fontFamily:
                              'Outfit, "Arial Black", var(--font-gilory), sans-serif',
                            fontSize: { xs: 28, lg: 25 },
                            lineHeight: 0.91,
                            fontWeight: 800,
                            letterSpacing: '-0.055em',
                          }}
                        >
                          {coverLabels[index % coverLabels.length].map(
                            (label, labelIndex) => (
                              <Box
                                key={label}
                                component='span'
                                sx={{
                                  color:
                                    labelIndex === 2 && index % 2 === 0
                                      ? '#ef1d1d'
                                      : '#080808',
                                }}
                              >
                                {label}
                              </Box>
                            ),
                          )}
                        </Stack>
                        <Box
                          sx={{
                            position: 'absolute',
                            left: 16,
                            bottom: 14,
                            zIndex: 1,
                            width: 28,
                            height: 28,
                            display: 'grid',
                            placeItems: 'center',
                            borderRadius: '50%',
                            bgcolor: '#fff',
                            border: '1px solid rgba(0,0,0,.08)',
                            fontSize: 14,
                          }}
                        >
                          {item.emoji || (
                            <DescriptionRounded sx={{ fontSize: 15 }} />
                          )}
                        </Box>
                      </Box>
                      <Typography
                        sx={{
                          mt: 2,
                          px: 1,
                          fontSize: 16,
                          fontWeight: 650,
                          lineHeight: 1.4,
                          letterSpacing: '-0.02em',
                          zIndex: 1,
                        }}
                      >
                        {item.name}
                      </Typography>
                      <Typography
                        className='ellipsis-2'
                        sx={{
                          mt: 0.8,
                          px: 1,
                          minHeight: 40,
                          color: '#777b84',
                          fontSize: 12,
                          lineHeight: 1.65,
                          zIndex: 1,
                        }}
                      >
                        {item.summary || '打开文档，继续阅读完整内容。'}
                      </Typography>
                      <Stack
                        direction='row'
                        alignItems='center'
                        gap={0.5}
                        sx={{
                          mt: 'auto',
                          mx: 1,
                          mb: 0.75,
                          pt: 1.75,
                          color: '#111',
                          fontSize: 12,
                          fontWeight: 600,
                          zIndex: 1,
                        }}
                      >
                        阅读文档
                        <ArrowForwardRounded
                          className='doc-arrow'
                          sx={{
                            fontSize: 15,
                            transition: 'transform .3s var(--niu-ease)',
                          }}
                        />
                      </Stack>
                    </Box>
                  ))}
                  {featureCards
                    .slice(0, Math.max(0, 4 - documents.length))
                    .map((feature, featureIndex) => {
                      const index = documents.length + featureIndex;
                      return (
                        <Box
                          key={feature.title}
                          role='button'
                          tabIndex={0}
                          onClick={() => submit(feature.prompt)}
                          onKeyDown={event => {
                            if (event.key === 'Enter' || event.key === ' ') {
                              event.preventDefault();
                              submit(feature.prompt);
                            }
                          }}
                          sx={{
                            minHeight: 360,
                            p: 1,
                            display: 'flex',
                            flexDirection: 'column',
                            borderRadius: '16px',
                            bgcolor: '#fff',
                            color: '#111',
                            border: '1px solid rgba(17,28,48,.09)',
                            boxShadow: '0 12px 34px rgba(17,28,48,.08)',
                            cursor: 'pointer',
                            transition:
                              'transform .25s ease, box-shadow .25s ease',
                            '&:hover': {
                              transform: 'translateY(-4px)',
                              boxShadow: '0 18px 46px rgba(17,28,48,.13)',
                              '& .doc-arrow': { transform: 'translateX(4px)' },
                            },
                          }}
                        >
                          <Box
                            sx={{
                              position: 'relative',
                              height: 186,
                              overflow: 'hidden',
                              borderRadius: '12px',
                              bgcolor: '#f3f0e8',
                              p: 2,
                              backgroundImage:
                                'linear-gradient(rgba(17,17,17,.045) 1px, transparent 1px), linear-gradient(90deg, rgba(17,17,17,.045) 1px, transparent 1px)',
                              backgroundSize: '22px 22px',
                              '&::before': {
                                content: '\"\"',
                                position: 'absolute',
                                width: 136,
                                height: 136,
                                right: -24,
                                bottom: -28,
                                borderRadius: index % 2 === 0 ? '50%' : '4px',
                                bgcolor: index % 2 === 0 ? '#ef1d1d' : '#111',
                              },
                              '&::after': {
                                content: '\"\"',
                                position: 'absolute',
                                right: 27,
                                bottom: -10,
                                width: 68,
                                height: 118,
                                border: '1px solid rgba(255,255,255,.75)',
                                borderRadius: '14px',
                                transform: 'rotate(-8deg)',
                              },
                            }}
                          >
                            <Stack
                              sx={{
                                position: 'relative',
                                zIndex: 1,
                                fontFamily:
                                  'Outfit, "Arial Black", var(--font-gilory), sans-serif',
                                fontSize: { xs: 28, lg: 25 },
                                lineHeight: 0.91,
                                fontWeight: 800,
                                letterSpacing: '-0.055em',
                              }}
                            >
                              {feature.labels.map((label, labelIndex) => (
                                <Box
                                  component='span'
                                  key={label}
                                  sx={{
                                    color:
                                      labelIndex === 2 ? '#ef1d1d' : '#080808',
                                  }}
                                >
                                  {label}
                                </Box>
                              ))}
                            </Stack>
                            <Box
                              sx={{
                                position: 'absolute',
                                left: 16,
                                bottom: 14,
                                zIndex: 1,
                                width: 28,
                                height: 28,
                                display: 'grid',
                                placeItems: 'center',
                                borderRadius: '50%',
                                bgcolor: '#fff',
                                border: '1px solid rgba(0,0,0,.08)',
                              }}
                            >
                              <AutoAwesomeRounded sx={{ fontSize: 15 }} />
                            </Box>
                          </Box>
                          <Typography
                            sx={{
                              mt: 2,
                              px: 1,
                              fontSize: 16,
                              fontWeight: 650,
                              lineHeight: 1.4,
                              letterSpacing: '-0.02em',
                            }}
                          >
                            {feature.title}
                          </Typography>
                          <Typography
                            className='ellipsis-2'
                            sx={{
                              mt: 0.8,
                              px: 1,
                              color: '#777b84',
                              fontSize: 12,
                              lineHeight: 1.65,
                            }}
                          >
                            {feature.desc}
                          </Typography>
                          <Stack
                            direction='row'
                            alignItems='center'
                            gap={0.5}
                            sx={{
                              mt: 'auto',
                              mx: 1,
                              mb: 0.75,
                              pt: 1.75,
                              color: '#111',
                              fontSize: 12,
                              fontWeight: 600,
                            }}
                          >
                            立即使用
                            <ArrowForwardRounded
                              className='doc-arrow'
                              sx={{
                                fontSize: 15,
                                transition: 'transform .3s ease',
                              }}
                            />
                          </Stack>
                        </Box>
                      );
                    })}
                </>
              ) : (
                <>
                  {[
                    {
                      title: '向知识库提问',
                      desc: '用自然语言描述问题，AI 会结合知识库内容给出回答。',
                      icon: <AutoAwesomeRounded />,
                    },
                    {
                      title: '精准搜索原文',
                      desc: '从相关文档片段出发，快速回到可信的上下文。',
                      icon: <SearchRounded />,
                    },
                    {
                      title: '知识持续生长',
                      desc: '新的文档会在发布后出现在这里，形成清晰的知识入口。',
                      icon: <FolderRounded />,
                    },
                  ].map((item, index) => (
                    <Box
                      key={item.title}
                      onClick={index === 0 ? () => submit() : undefined}
                      sx={theme => ({
                        minHeight: 250,
                        p: 3.5,
                        borderRadius: '28px',
                        bgcolor: index === 0 ? '#101114' : 'background.paper',
                        color: index === 0 ? '#fff' : 'text.primary',
                        border: `1px solid ${alpha(theme.palette.text.primary, 0.075)}`,
                        cursor: index === 0 ? 'pointer' : 'default',
                      })}
                    >
                      <Box
                        sx={{
                          color: index === 0 ? '#64b5ff' : 'primary.main',
                          fontSize: 28,
                        }}
                      >
                        {item.icon}
                      </Box>
                      <Typography sx={{ mt: 7, fontSize: 22, fontWeight: 680 }}>
                        {item.title}
                      </Typography>
                      <Typography
                        sx={{
                          mt: 1.25,
                          color:
                            index === 0
                              ? 'rgba(255,255,255,.62)'
                              : 'text.tertiary',
                          fontSize: 14,
                          lineHeight: 1.7,
                        }}
                      >
                        {item.desc}
                      </Typography>
                    </Box>
                  ))}
                </>
              )}
            </Box>
          )}

          <Box
            ref={waterfallRef}
            aria-label='知识库瀑布流'
            sx={{
              columnCount: { xs: 1, sm: 2, md: 3, lg: 4 },
              columnGap: { xs: 2, md: 2.25 },
            }}
          >
            {documents.map((item, index) => {
              const coverHeight = [238, 302, 214, 272][index % 4];
              const accent = ['#ef3025', '#111', '#315cff', '#ffb000'][
                index % 4
              ];
              return (
                <Box
                  component={Link}
                  href={`${basePath}/node/${item.id}`}
                  key={`waterfall-document-${item.id}`}
                  sx={{
                    width: '100%',
                    mb: { xs: 2, md: 2.25 },
                    breakInside: 'avoid',
                    display: 'inline-flex',
                    verticalAlign: 'top',
                    flexDirection: 'column',
                    overflow: 'hidden',
                    borderRadius: '16px',
                    bgcolor: '#fff',
                    color: '#111',
                    border: '1px solid rgba(17,28,48,.09)',
                    boxShadow: '0 10px 28px rgba(17,28,48,.07)',
                    transition: 'transform .24s ease, box-shadow .24s ease',
                    '&:hover': {
                      transform: 'translateY(-4px)',
                      boxShadow: '0 18px 42px rgba(17,28,48,.12)',
                    },
                  }}
                >
                  <Box
                    sx={{
                      position: 'relative',
                      height: coverHeight,
                      overflow: 'hidden',
                      bgcolor: '#f3f0e8',
                      p: 2.25,
                      backgroundImage:
                        'linear-gradient(rgba(17,17,17,.05) 1px, transparent 1px), linear-gradient(90deg, rgba(17,17,17,.05) 1px, transparent 1px)',
                      backgroundSize: '22px 22px',
                      '&::before': {
                        content: '""',
                        position: 'absolute',
                        width: coverHeight * 0.72,
                        height: coverHeight * 0.72,
                        right: -coverHeight * 0.15,
                        bottom: -coverHeight * 0.2,
                        borderRadius: index % 2 === 0 ? '50%' : '24px',
                        bgcolor: accent,
                        transform: `rotate(${index % 2 === 0 ? 0 : 13}deg)`,
                      },
                      '&::after': {
                        content: '""',
                        position: 'absolute',
                        right: 30,
                        bottom: -20,
                        width: 76,
                        height: coverHeight * 0.62,
                        border: '1px solid rgba(255,255,255,.75)',
                        borderRadius: '18px',
                        transform: 'rotate(-7deg)',
                      },
                    }}
                  >
                    <Stack
                      sx={{
                        position: 'relative',
                        zIndex: 1,
                        width: '76%',
                        fontFamily:
                          'Outfit, "Arial Black", var(--font-gilory), sans-serif',
                        fontSize: { xs: 28, lg: 25 },
                        lineHeight: 0.9,
                        fontWeight: 850,
                        letterSpacing: '-0.055em',
                      }}
                    >
                      {coverLabels[index % coverLabels.length].map(
                        (label, labelIndex) => (
                          <Box
                            component='span'
                            key={label}
                            sx={{
                              color:
                                labelIndex === 2 && index % 2 === 0
                                  ? accent
                                  : '#080808',
                            }}
                          >
                            {label}
                          </Box>
                        ),
                      )}
                    </Stack>
                    <Box
                      sx={{
                        position: 'absolute',
                        left: 18,
                        bottom: 16,
                        zIndex: 1,
                        px: 1.05,
                        py: 0.5,
                        borderRadius: '999px',
                        bgcolor: '#fff',
                        border: '1px solid rgba(0,0,0,.08)',
                        fontSize: 10.5,
                        fontWeight: 650,
                      }}
                    >
                      文档
                    </Box>
                  </Box>
                  <Box sx={{ p: 2 }}>
                    <Typography
                      sx={{ fontSize: 15.5, fontWeight: 680, lineHeight: 1.45 }}
                    >
                      {item.name}
                    </Typography>
                    <Typography
                      className='ellipsis-2'
                      sx={{
                        mt: 0.8,
                        color: '#7f8289',
                        fontSize: 12,
                        lineHeight: 1.65,
                      }}
                    >
                      {item.summary || '打开文档，继续阅读完整内容。'}
                    </Typography>
                    <Stack
                      direction='row'
                      alignItems='center'
                      gap={0.5}
                      sx={{ mt: 1.7, fontSize: 11.5, fontWeight: 650 }}
                    >
                      阅读文档
                      <ArrowForwardRounded sx={{ fontSize: 14 }} />
                    </Stack>
                  </Box>
                </Box>
              );
            })}

            {featureCards.map((feature, featureIndex) => {
              const index = documents.length + featureIndex;
              const coverHeight = [194, 258, 222, 288, 208][index % 5];
              const palette = [
                ['#101114', '#c8ff39'],
                ['#f3efe6', '#ef3025'],
                ['#14285f', '#a7c8ff'],
                ['#4c167f', '#ffe766'],
                ['#073f3a', '#ffb3c4'],
              ][index % 5];
              const dark = index % 5 !== 1;
              return (
                <Box
                  component='button'
                  type='button'
                  key={`waterfall-feature-${feature.title}`}
                  onClick={() => submit(feature.prompt)}
                  sx={{
                    width: '100%',
                    mb: { xs: 2, md: 2.25 },
                    p: 0,
                    breakInside: 'avoid',
                    display: 'inline-flex',
                    verticalAlign: 'top',
                    flexDirection: 'column',
                    overflow: 'hidden',
                    borderRadius: '16px',
                    bgcolor: '#fff',
                    color: '#111',
                    border: '1px solid rgba(17,28,48,.09)',
                    boxShadow: '0 10px 28px rgba(17,28,48,.065)',
                    font: 'inherit',
                    textAlign: 'left',
                    cursor: 'pointer',
                    transition: 'transform .24s ease, box-shadow .24s ease',
                    '&:hover': {
                      transform: 'translateY(-4px)',
                      boxShadow: '0 18px 42px rgba(17,28,48,.12)',
                    },
                  }}
                >
                  <Box
                    sx={{
                      position: 'relative',
                      width: '100%',
                      height: coverHeight,
                      overflow: 'hidden',
                      bgcolor: palette[0],
                      color: dark ? '#fff' : '#111',
                      p: 2.25,
                      backgroundImage:
                        'linear-gradient(rgba(255,255,255,.09) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.09) 1px, transparent 1px)',
                      backgroundSize: '24px 24px',
                      '&::after': {
                        content: '""',
                        position: 'absolute',
                        width: coverHeight * 0.72,
                        height: coverHeight * 0.72,
                        right: -coverHeight * 0.17,
                        bottom: -coverHeight * 0.22,
                        borderRadius: featureIndex % 2 === 0 ? '50%' : '28px',
                        bgcolor: palette[1],
                        transform: `rotate(${featureIndex % 2 === 0 ? 0 : 16}deg)`,
                      },
                    }}
                  >
                    <Stack
                      sx={{
                        position: 'relative',
                        zIndex: 1,
                        width: '78%',
                        fontFamily:
                          'Outfit, "Arial Black", var(--font-gilory), sans-serif',
                        fontSize: { xs: 28, lg: 24 },
                        lineHeight: 0.9,
                        fontWeight: 850,
                        letterSpacing: '-0.055em',
                      }}
                    >
                      {feature.labels.map((label, labelIndex) => (
                        <Box
                          component='span'
                          key={label}
                          sx={{
                            color:
                              labelIndex === 2
                                ? palette[1]
                                : dark
                                  ? '#fff'
                                  : '#111',
                          }}
                        >
                          {label}
                        </Box>
                      ))}
                    </Stack>
                    <Box
                      sx={{
                        position: 'absolute',
                        left: 18,
                        bottom: 16,
                        zIndex: 1,
                        px: 1.05,
                        py: 0.5,
                        borderRadius: '999px',
                        bgcolor: dark ? 'rgba(255,255,255,.14)' : '#fff',
                        border: `1px solid ${dark ? 'rgba(255,255,255,.2)' : 'rgba(0,0,0,.08)'}`,
                        color: dark ? '#fff' : '#111',
                        fontSize: 10.5,
                        fontWeight: 650,
                      }}
                    >
                      AI 知识入口
                    </Box>
                  </Box>
                  <Box sx={{ p: 2 }}>
                    <Typography
                      sx={{ fontSize: 15.5, fontWeight: 680, lineHeight: 1.45 }}
                    >
                      {feature.title}
                    </Typography>
                    <Typography
                      sx={{
                        mt: 0.8,
                        color: '#7f8289',
                        fontSize: 12,
                        lineHeight: 1.65,
                      }}
                    >
                      {feature.desc}
                    </Typography>
                    <Stack
                      direction='row'
                      alignItems='center'
                      gap={0.5}
                      sx={{ mt: 1.7, fontSize: 11.5, fontWeight: 650 }}
                    >
                      立即使用
                      <ArrowForwardRounded sx={{ fontSize: 14 }} />
                    </Stack>
                  </Box>
                </Box>
              );
            })}
          </Box>

          <Box
            aria-label='瀑布流悬浮问答框'
            aria-hidden={!showFloatingComposer}
            sx={{
              position: 'fixed',
              left: '50%',
              bottom: { xs: 12, sm: 22 },
              zIndex: 108,
              width: {
                xs: 'calc(100vw - 24px)',
                sm: 'min(820px, calc(100vw - 48px))',
              },
              p: { xs: 1.2, sm: 1.5 },
              borderRadius: { xs: '14px', sm: '16px' },
              bgcolor: 'rgba(255,255,255,.96)',
              border: '1px solid rgba(17,28,48,.11)',
              boxShadow:
                '0 22px 70px rgba(17,28,48,.18), 0 4px 14px rgba(17,28,48,.08)',
              backdropFilter: 'saturate(145%) blur(20px)',
              opacity: showFloatingComposer ? 1 : 0,
              visibility: showFloatingComposer ? 'visible' : 'hidden',
              transform: showFloatingComposer
                ? 'translate(-50%, 0)'
                : 'translate(-50%, 18px)',
              pointerEvents: showFloatingComposer ? 'auto' : 'none',
              transition: `opacity .24s ease, transform .32s cubic-bezier(.22,1,.36,1), visibility 0s linear ${showFloatingComposer ? '0s' : '.32s'}`,
            }}
          >
            <Stack sx={{ minHeight: { xs: 104, sm: 118 } }}>
              <Stack direction='row' alignItems='center' gap={0.8}>
                <Box
                  sx={{
                    width: 27,
                    height: 27,
                    display: 'grid',
                    placeItems: 'center',
                    flexShrink: 0,
                    color: '#fff',
                    bgcolor: '#111',
                    borderRadius: '50%',
                  }}
                >
                  <AutoAwesomeRounded sx={{ fontSize: 14 }} />
                </Box>
                <Typography
                  sx={{ color: '#5f626a', fontSize: 12, fontWeight: 600 }}
                >
                  知识检索与 AI 问答
                </Typography>
              </Stack>

              <InputBase
                fullWidth
                multiline
                maxRows={2}
                value={query}
                onChange={event => setQuery(event.target.value)}
                onKeyDown={event => {
                  if (
                    event.key === 'Enter' &&
                    !event.shiftKey &&
                    !event.nativeEvent.isComposing
                  ) {
                    event.preventDefault();
                    submit();
                  }
                }}
                inputProps={{ 'aria-label': '在瀑布流中向知识库提问' }}
                placeholder='输入问题、关键词或文档主题，让知识库帮你找到答案。'
                sx={{
                  flex: 1,
                  alignItems: 'flex-start',
                  px: 0.25,
                  pt: 1,
                  fontSize: { xs: 13.5, sm: 14.5 },
                  lineHeight: 1.6,
                  '& textarea::placeholder': {
                    color: '#999ca3',
                    opacity: 1,
                  },
                }}
              />

              <Stack
                direction='row'
                alignItems='center'
                justifyContent='space-between'
              >
                <Stack direction='row' alignItems='center' gap={0.5}>
                  <IconButton
                    aria-label='从悬浮问答框上传图片'
                    size='small'
                    onClick={() => imageInputRef.current?.click()}
                    sx={{ color: '#777b84' }}
                  >
                    <AddCircleOutlineRounded sx={{ fontSize: 19 }} />
                  </IconButton>
                  <IconButton
                    aria-label='从悬浮问答框上传文件'
                    size='small'
                    onClick={() => fileInputRef.current?.click()}
                    sx={{ color: '#777b84' }}
                  >
                    <AttachFileRounded sx={{ fontSize: 18 }} />
                  </IconButton>
                </Stack>
                <Stack direction='row' alignItems='center' gap={1}>
                  <Button
                    variant='outlined'
                    size='small'
                    startIcon={<AutoAwesomeRounded sx={{ fontSize: 14 }} />}
                    sx={{
                      display: { xs: 'none', sm: 'inline-flex' },
                      minHeight: 32,
                      px: 1.4,
                      color: '#696c73',
                      borderColor: '#e1e2e5',
                      fontSize: 11.5,
                    }}
                  >
                    深度思考
                  </Button>
                  <IconButton
                    aria-label='从悬浮问答框开始智能问答'
                    onClick={() => submit()}
                    sx={{
                      width: 38,
                      height: 38,
                      color: '#fff',
                      bgcolor: '#8d8d8d',
                      '&:hover': { bgcolor: '#707070' },
                    }}
                  >
                    <SendRounded sx={{ fontSize: 18 }} />
                  </IconButton>
                </Stack>
              </Stack>
            </Stack>
          </Box>
        </Box>
      </Box>
    </Box>
  );
};

const Welcome = () => {
  const basePath = useBasePath();
  const {
    mobile = false,
    kbDetail,
    setQaModalOpen,
    qaDraftFiles,
    setQaDraftFiles,
    setQaConversationTarget,
    tree = [],
  } = useStore();
  const historyScope = useMemo(
    () => getConversationHistoryScope(kbDetail?.name, kbDetail?.base_url),
    [kbDetail?.name, kbDetail?.base_url],
  );
  const [histories, setHistories] = useState<ConversationHistoryEntry[]>([]);

  useEffect(() => {
    const syncHistory = (event?: Event) => {
      if (
        event instanceof CustomEvent &&
        event.detail?.scope &&
        event.detail.scope !== historyScope
      ) {
        return;
      }
      setHistories(readConversationHistory(historyScope));
    };
    syncHistory();
    window.addEventListener(CONVERSATION_HISTORY_EVENT, syncHistory);
    window.addEventListener('storage', syncHistory);
    return () => {
      window.removeEventListener(CONVERSATION_HISTORY_EVENT, syncHistory);
      window.removeEventListener('storage', syncHistory);
    };
  }, [historyScope]);
  const settings = kbDetail?.settings;
  const onBannerSearch = (
    searchText: string,
    type: 'chat' | 'search' = 'chat',
  ) => {
    if (searchText.trim()) {
      if (type === 'chat') {
        sessionStorage.setItem('chat_search_query', searchText.trim());
        setQaModalOpen?.(true);
      } else {
        sessionStorage.setItem('chat_search_query', searchText.trim());
      }
    }
  };

  const onAttach = (files: File[]) => {
    if (files.length === 0) return;
    setQaDraftFiles?.([...(qaDraftFiles || []), ...files]);
    setQaModalOpen?.(true);
  };

  const onHistoryOpen = (history: ConversationHistoryEntry) => {
    setQaConversationTarget?.({ id: history.id, nonce: history.nonce });
    setQaModalOpen?.(true);
  };

  const TYPE_TO_CONFIG_LABEL = {
    banner: 'banner_config',
    basic_doc: 'basic_doc_config',
    nav_doc: 'nav_doc_config',
    dir_doc: 'dir_doc_config',
    simple_doc: 'simple_doc_config',
    carousel: 'carousel_config',
    faq: 'faq_config',
    text: 'text_config',
    case: 'case_config',
    metrics: 'metrics_config',
    feature: 'feature_config',
    text_img: 'text_img_config',
    img_text: 'img_text_config',
    comment: 'comment_config',
    block_grid: 'block_grid_config',
    question: 'question_config',
  } as const;

  const handleComponentProps = (data: any) => {
    const config =
      data[
        TYPE_TO_CONFIG_LABEL[data.type as keyof typeof TYPE_TO_CONFIG_LABEL]
      ];

    switch (data.type) {
      case 'faq':
        return handleFaqProps(config);
      case 'basic_doc':
        return handleBasicDocProps(config, data.nodes, basePath);
      case 'dir_doc':
        return handleDirDocProps(config, data.nodes, basePath);
      case 'nav_doc':
        return handleNavDocProps(config, data.nodes, basePath);
      case 'simple_doc':
        return handleSimpleDocProps(config, data.nodes, basePath);
      case 'carousel':
        return handleCarouselProps(config, basePath);
      case 'banner':
        return {
          ...handleBannerProps(config, basePath),
          onSearch: onBannerSearch,
          btns: (config?.btns || []).map((item: any) => ({
            ...item,
            href: getImagePath(item.href || '/node', basePath),
          })),
        };
      case 'text':
        return handleTextProps(config);
      case 'case':
        return handleCaseProps(config);
      case 'metrics':
        return handleMetricsProps(config);
      case 'feature':
        return handleFeatureProps(config);
      case 'text_img':
        return handleTextImgProps(config, basePath);
      case 'img_text':
        return handleImgTextProps(config, basePath);
      case 'comment':
        return handleCommentProps(config, basePath);
      case 'block_grid':
        return handleBlockGridProps(config, basePath);
      case 'question':
        return {
          ...handleQuestionProps(config),
          onSearch: (text: string) => {
            onBannerSearch(text, 'chat');
          },
        };
    }
  };

  const landingConfigs = settings?.web_app_landing_configs || [];
  if (landingConfigs.length === 0) {
    return (
      <DefaultLanding
        tree={tree}
        basePath={basePath}
        onSearch={onBannerSearch}
        onAttach={onAttach}
        histories={histories}
        onHistoryOpen={onHistoryOpen}
      />
    );
  }

  return (
    <>
      {landingConfigs.map((item, index) => {
        const Component = componentMap[item.type as keyof typeof componentMap];
        const props = handleComponentProps(item);
        return Component ? (
          // @ts-ignore
          <Component key={index} mobile={mobile} {...props} />
        ) : null;
      })}
    </>
  );
};

export default Welcome;
