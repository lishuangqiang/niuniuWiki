'use client';
import aiLoading from '@/assets/images/ai-loading.gif';
import { ChunkResultItem } from '@/assets/type';
import Feedback from '@/components/feedback';
import { IconCopy } from '@/components/icons';
import MarkDown2 from '@/components/markdown2';
import { useBasePath, useSmartScroll } from '@/hooks';
import { useStore } from '@/provider';
import { postShareV1ChatFeedback } from '@/request/ShareChat';
import { getShareV1ConversationDetail } from '@/request/ShareConversation';
import { postShareV1CommonFileUpload } from '@/request/ShareFile';
import { copyText } from '@/utils';
import {
  getConversationHistoryScope,
  readConversationHistory,
  rememberConversation,
} from '@/utils/conversationHistory';
import { reconcileCitedReferences } from '@/utils/citations';
import {
  AgentRunEvent,
  appendAgentEvent,
  handleThinkingContent,
  thinkingStageForAgentEvent,
  upsertReference,
} from '@/utils/chatEngine';
import SSEClient, { SSEHttpError } from '@/utils/fetch';
import { Image as ImagePreview, message } from '@ctzhian/ui';
import CloseIcon from '@mui/icons-material/Close';
import AttachFileRounded from '@mui/icons-material/AttachFileRounded';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {
  Box,
  Button,
  IconButton,
  Stack,
  Typography,
  alpha,
  useTheme,
} from '@mui/material';
import {
  IconADiancaiWeixuanzhong2,
  IconDiancaiWeixuanzhong,
  IconDianzanWeixuanzhong,
  IconDianzanXuanzhong1,
  IconFasong,
  IconTupian,
  IconXinduihua,
} from '@niuniu-wiki/icons';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import relativeTime from 'dayjs/plugin/relativeTime';
import Image from 'next/image';
import { useSearchParams } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { v4 as uuidv4 } from 'uuid';
import ChatLoading from '../../views/chat/ChatLoading';
import {
  StyledActionButtonStack,
  StyledActionStack,
  StyledAiBubble,
  StyledAiBubbleContent,
  StyledChunkAccordion,
  StyledChunkAccordionDetails,
  StyledChunkAccordionSummary,
  StyledChunkItem,
  StyledConversationContainer,
  StyledConversationItem,
  StyledFuzzySuggestionItem,
  StyledFuzzySuggestionsStack,
  StyledImagePreviewItem,
  StyledImagePreviewStack,
  StyledImageRemoveButton,
  StyledInputContainer,
  StyledInputWrapper,
  StyledMainContainer,
  StyledTextField,
  StyledThinkingAccordion,
  StyledThinkingAccordionDetails,
  StyledThinkingAccordionSummary,
  StyledUserBubble,
} from './StyledComponents';
import { getImagePath } from '@/utils/getImagePath';

export interface ConversationItem {
  image_paths: string[];
  q: string;
  a: string;
  score: number;
  update_time: string;
  message_id: string;
  source: 'history' | 'chat';
  chunk_result: ChunkResultItem[];
  result_expend: boolean;
  thinking_expend: boolean;
  thinking_content: string;
  agent_events?: AgentRunEvent[];
  agent_mode?: string;
  id: string;
  attachments?: Array<{
    name: string;
    size: number;
    type: string;
  }>;
}

interface TextAttachment {
  id: string;
  name: string;
  size: number;
  type: string;
  content: string;
}

const TEXT_ATTACHMENT_EXTENSIONS = new Set([
  'txt',
  'md',
  'csv',
  'json',
  'html',
  'xml',
  'yaml',
  'yml',
  'log',
]);

const formatAttachmentSize = (size: number) =>
  size < 1024
    ? `${size} B`
    : size < 1024 * 1024
      ? `${(size / 1024).toFixed(1)} KB`
      : `${(size / 1024 / 1024).toFixed(1)} MB`;

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

const AnswerStatus = {
  1: '正在搜索结果...',
  2: '思考中...',
  3: '正在回答',
  4: '',
};

const LoadingContent = ({
  thinking,
  status,
}: {
  thinking: keyof typeof AnswerStatus;
  status?: string;
}) => {
  if (thinking === 4 || (thinking === 2 && !status)) return null;
  return (
    <Stack direction='row' alignItems='center' gap={1} sx={{ pb: 1 }}>
      <Image
        src={aiLoading}
        alt='ai-loading'
        unoptimized
        width={20}
        height={20}
      />
      <Typography
        variant='body2'
        sx={theme => ({
          fontSize: 12,
          color: alpha(theme.palette.text.primary, 0.5),
        })}
      >
        {status || AnswerStatus[thinking]}
      </Typography>
    </Stack>
  );
};

const AGENT_STAGE_LABELS: Record<string, string> = {
  understand: '理解问题',
  plan: '制定计划',
  retrieve: '执行检索',
  reflect: '检查证据',
  generate: '组织答案',
  clarify: '请求澄清',
  resume: '恢复运行',
  complete: '完成',
};

const AGENT_MODE_LABELS: Record<string, string> = {
  NONE: '无需检索',
  SINGLE: '单次检索',
  PARALLEL: '并行多查询',
  MULTI_HOP: '链式多跳',
  CLARIFY: '需要澄清',
};

const AgentTrace = ({
  events,
  mode,
  active,
}: {
  events: AgentRunEvent[];
  mode?: string;
  active: boolean;
}) => {
  if (!events.length) return null;
  const visible = events
    .filter(
      (event, index) =>
        event.status !== 'PROGRESS' || index === events.length - 1,
    )
    .slice(-7);
  return (
    <Box
      sx={theme => ({
        width: '100%',
        maxWidth: 620,
        borderLeft: `1px solid ${alpha(theme.palette.text.primary, 0.12)}`,
        pl: 2,
        py: 0.25,
      })}
    >
      <Stack direction='row' alignItems='center' gap={1} sx={{ mb: 1.25 }}>
        <Typography sx={{ fontSize: 12, fontWeight: 700 }}>
          Adaptive RAG
        </Typography>
        {mode && (
          <Typography
            sx={theme => ({
              px: 0.9,
              py: 0.25,
              borderRadius: 999,
              fontSize: 10.5,
              color: alpha(theme.palette.text.primary, 0.58),
              bgcolor: alpha(theme.palette.text.primary, 0.045),
            })}
          >
            {AGENT_MODE_LABELS[mode] || mode}
          </Typography>
        )}
      </Stack>
      <Stack gap={0.8}>
        {visible.map((event, index) => (
          <Stack
            key={`${event.stage}-${event.status}-${event.iteration}-${index}`}
            direction='row'
            alignItems='flex-start'
            gap={1}
          >
            <Box
              sx={theme => ({
                width: 6,
                height: 6,
                mt: '6px',
                borderRadius: '50%',
                flexShrink: 0,
                bgcolor:
                  active && index === visible.length - 1
                    ? theme.palette.text.primary
                    : alpha(theme.palette.text.primary, 0.25),
              })}
            />
            <Typography
              sx={theme => ({
                fontSize: 11.5,
                lineHeight: 1.65,
                color: alpha(theme.palette.text.primary, 0.58),
              })}
            >
              <Box component='span' sx={{ fontWeight: 650, mr: 0.75 }}>
                {AGENT_STAGE_LABELS[event.stage] || event.stage}
              </Box>
              {event.message}
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
};

const AiQaContent: React.FC<{
  hotSearch: string[];
  placeholder: string;
  inputRef: React.RefObject<HTMLInputElement | null>;
  activeReferenceId?: string;
  onReferencesChange: (references: ChunkResultItem[]) => void;
  onReferenceSelect: (
    reference: ChunkResultItem,
    source?: ChunkResultItem[],
  ) => void;
  newConversationToken: number;
}> = ({
  hotSearch,
  placeholder,
  inputRef,
  activeReferenceId,
  onReferencesChange,
  onReferenceSelect,
  newConversationToken,
}) => {
  const sseClientRef = useRef<SSEClient<{
    type: string;
    content: string;
    chunk_result: ChunkResultItem;
    agent_event?: AgentRunEvent;
  }> | null>(null);
  const { palette } = useTheme();
  const {
    mobile = false,
    kbDetail,
    qaModalOpen,
    qaDraftFiles = [],
    setQaDraftFiles,
    qaConversationTarget,
    setQaConversationTarget,
  } = useStore();
  const messageIdRef = useRef('');
  const lastResultExpendRef = useRef(false);
  const [fullAnswer, setFullAnswer] = useState<string>('');
  const [conversation, setConversation] = useState<ConversationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [thinking, setThinking] = useState<keyof typeof AnswerStatus>(4);
  const [agentStatus, setAgentStatus] = useState('');
  const [nonce, setNonce] = useState('');
  const [conversationId, setConversationId] = useState('');
  const [input, setInput] = useState('');
  const [open, setOpen] = useState(false);
  const [conversationItem, setConversationItem] =
    useState<ConversationItem | null>(null);
  const [uploadedImages, setUploadedImages] = useState<
    Array<{
      id: string;
      url: string;
      file: File;
    }>
  >([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const documentInputRef = useRef<HTMLInputElement>(null);
  const [uploadedFiles, setUploadedFiles] = useState<TextAttachment[]>([]);
  const [fuzzySuggestions, setFuzzySuggestions] = useState<string[]>([]);
  const [showFuzzySuggestions, setShowFuzzySuggestions] = useState(false);

  const searchParams = useSearchParams();
  const basePath = useBasePath();
  const historyScope = getConversationHistoryScope(
    kbDetail?.name,
    kbDetail?.base_url,
  );
  const activeConversationIdRef = useRef('');
  const activeNonceRef = useRef('');
  const historyTitleRef = useRef('');
  const historyPreviewRef = useRef('');
  const historyMessageCountRef = useRef(1);
  const latestReferencesRef = useRef<ChunkResultItem[]>([]);
  const activeRunIdRef = useRef('');
  const manualAbortRef = useRef(false);
  const resetTokenRef = useRef(newConversationToken);

  // 使用智能滚动 hook（内置 ResizeObserver 自动监听内容高度变化，自动滚动）
  const { setShouldAutoScroll } = useSmartScroll({
    container: '.conversation-container',
    behavior: 'smooth',
  });

  const onReset = () => {
    if (loading) {
      handleSearchAbort();
    }
    setConversationId('');
    setConversation([]);
    setFullAnswer('');
    setInput('');
    // 清理图片URL
    uploadedImages.forEach(img => {
      if (img.url.startsWith('blob:')) {
        URL.revokeObjectURL(img.url);
      }
    });
    setUploadedImages([]);
    setUploadedFiles([]);
    setLoading(false);
    setAgentStatus('');
    activeRunIdRef.current = '';
    setNonce('');
    activeConversationIdRef.current = '';
    activeNonceRef.current = '';
    historyTitleRef.current = '';
    historyPreviewRef.current = '';
    historyMessageCountRef.current = 1;
    latestReferencesRef.current = [];
    onReferencesChange([]);
    const currentUrl = new URL(window.location.href);
    currentUrl.searchParams.delete('cid');
    currentUrl.searchParams.delete('ask');
    window.history.replaceState(null, '', currentUrl.toString());
  };

  useEffect(() => {
    if (newConversationToken === resetTokenRef.current) return;
    resetTokenRef.current = newConversationToken;
    onReset();
  }, [newConversationToken]);

  const handleSearch = (reset: boolean = false) => {
    if (
      input.length > 0 ||
      uploadedImages.length > 0 ||
      uploadedFiles.length > 0
    ) {
      onSearch(input, reset);
    }
  };

  const onSuggestionClick = (text: string) => {
    setInput('');
    onSearch(text);
  };

  // 处理图片选择（支持多张）
  const handleImageSelect = async (files: FileList | File[] | null) => {
    if (!files || files.length === 0) return;

    const maxImages = 3;
    const remainingSlots = maxImages - uploadedImages.length;
    if (remainingSlots <= 0) {
      message.warning(`最多只能上传 ${maxImages} 张图片`);
      return;
    }

    const filesToAdd = Array.from(files).slice(0, remainingSlots);

    try {
      const newImages: Array<{
        id: string;
        url: string;
        file: File;
      }> = [];

      for (const file of filesToAdd) {
        // 验证文件类型
        if (!file.type.startsWith('image/')) {
          message.error('只支持上传图片文件');
          continue;
        }

        // 验证文件大小 (10MB)
        if (file.size > 10 * 1024 * 1024) {
          message.error('图片大小不能超过 10MB');
          continue;
        }

        // 创建本地预览 URL
        const localUrl = URL.createObjectURL(file);

        newImages.push({
          id: Date.now().toString() + Math.random(),
          url: localUrl,
          file,
        });
      }

      const updatedImages = [...uploadedImages, ...newImages];
      setUploadedImages(updatedImages);
    } catch (error: any) {
      message.error(error.message || '图片选择失败');
    }
  };

  const handleImageUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    handleImageSelect(event.target.files);
    // 重置 input value 以允许上传相同文件
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleRemoveImage = (id: string) => {
    const imageToRemove = uploadedImages.find(img => img.id === id);
    if (imageToRemove && imageToRemove.url.startsWith('blob:')) {
      // 释放本地 URL
      URL.revokeObjectURL(imageToRemove.url);
    }

    const updatedImages = uploadedImages.filter(img => img.id !== id);
    setUploadedImages(updatedImages);
  };

  const handleDocumentSelect = async (files: FileList | File[] | null) => {
    if (!files || files.length === 0) return;

    const remainingSlots = 3 - uploadedFiles.length;
    if (remainingSlots <= 0) {
      message.warning('最多只能上传 3 个文件');
      return;
    }

    const selected = Array.from(files).slice(0, remainingSlots);
    const next: TextAttachment[] = [];
    for (const file of selected) {
      const extension = file.name.split('.').pop()?.toLowerCase() || '';
      if (!TEXT_ATTACHMENT_EXTENSIONS.has(extension)) {
        message.error(
          `${file.name} 暂不支持，请上传 TXT、Markdown、CSV、JSON、HTML、XML 或 YAML 文件`,
        );
        continue;
      }
      if (file.size > 2 * 1024 * 1024) {
        message.error(`${file.name} 不能超过 2MB`);
        continue;
      }
      try {
        const content = (await file.text()).slice(0, 30_000).trim();
        if (!content) {
          message.warning(`${file.name} 没有可读取的文本内容`);
          continue;
        }
        next.push({
          id: `${Date.now()}-${Math.random()}`,
          name: file.name,
          size: file.size,
          type: file.type || 'text/plain',
          content,
        });
      } catch {
        message.error(`${file.name} 读取失败`);
      }
    }
    setUploadedFiles(previous => [...previous, ...next]);
  };

  const handleDocumentUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    void handleDocumentSelect(event.target.files);
    event.target.value = '';
  };

  const handleRemoveFile = (id: string) => {
    setUploadedFiles(previous => previous.filter(file => file.id !== id));
  };

  useEffect(() => {
    if (!qaModalOpen || qaDraftFiles.length === 0) return;
    const images = qaDraftFiles.filter(file => file.type.startsWith('image/'));
    const documents = qaDraftFiles.filter(
      file => !file.type.startsWith('image/'),
    );
    void handleImageSelect(images);
    void handleDocumentSelect(documents);
    setQaDraftFiles?.([]);
    // 首页附件只消费一次；处理函数依赖当前附件数量，避免重复选择时覆盖已有文件。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [qaModalOpen, qaDraftFiles]);

  // 处理粘贴上传
  const handlePaste = async (e: React.ClipboardEvent<HTMLDivElement>) => {
    const items = e.clipboardData?.items;
    if (!items) return;

    const imageFiles: File[] = [];
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.type.startsWith('image/')) {
        const file = item.getAsFile();
        if (file) {
          imageFiles.push(file);
        }
      }
    }

    if (imageFiles.length > 0) {
      e.preventDefault();
      const dataTransfer = new DataTransfer();
      imageFiles.forEach(file => dataTransfer.items.add(file));
      await handleImageSelect(dataTransfer.files);
    }
  };

  // 处理输入变化，显示模糊搜索建议
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setInput(value);

    // if (value.trim().length > 0) {
    //   // 改进的模糊搜索逻辑
    //   const filtered = mockFuzzySuggestions
    //     .filter(suggestion => {
    //       const lowerSuggestion = suggestion.toLowerCase();
    //       const lowerValue = value.toLowerCase();
    //       // 支持前缀匹配和包含匹配
    //       return (
    //         lowerSuggestion.startsWith(lowerValue) ||
    //         lowerSuggestion.includes(lowerValue)
    //       );
    //     })
    //     .slice(0, 5); // 限制显示数量

    //   setFuzzySuggestions(filtered);
    //   setShowFuzzySuggestions(true);
    // } else {
    //   setShowFuzzySuggestions(false);
    //   setFuzzySuggestions([]);
    // }
  };

  // 选择模糊搜索建议
  const handleFuzzySuggestionClick = (suggestion: string) => {
    setInput(suggestion);
    setShowFuzzySuggestions(false);
    setFuzzySuggestions([]);
  };

  // 高亮显示匹配的文本
  const highlightMatch = (text: string, query: string) => {
    if (!query.trim()) return text;

    // 转义特殊字符，避免正则表达式错误
    const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`(${escapedQuery})`, 'gi');
    const parts = text.split(regex);

    return parts.map((part, index) => {
      // 检查是否匹配（不区分大小写）
      if (part.toLowerCase() === query.toLowerCase()) {
        return (
          <Box
            component='span'
            key={index}
            sx={{
              color: 'primary.main',
            }}
          >
            {part}
          </Box>
        );
      }
      return part;
    });
  };

  // 处理输入框失去焦点
  const handleInputBlur = () => {
    // 延迟隐藏，让用户有时间点击建议
    setTimeout(() => {
      setShowFuzzySuggestions(false);
    }, 200);
  };

  // 处理输入框获得焦点
  const handleInputFocus = () => {
    if (input.trim().length > 0) {
      setShowFuzzySuggestions(true);
    }
  };

  // 上传所有图片到服务器
  const uploadAllImages = async (): Promise<string[]> => {
    if (uploadedImages.length === 0) return [];

    const uploadedUrls: string[] = [];

    try {
      for (const image of uploadedImages) {
        let token = '';
        try {
          const Cap = (await import(`@cap.js/widget`)).default;
          const cap = new Cap({
            apiEndpoint: `${basePath}/share/v1/captcha/`,
          });
          const solution = await cap.solve();
          token = solution.token;
        } catch (error) {
          message.error('验证失败');
          return Promise.reject(error);
        }
        // 上传新图片
        const result = await postShareV1CommonFileUpload({
          file: image.file,
          captcha_token: token,
        });
        const serverUrl = '/static-file/' + result.key;
        uploadedUrls.push(serverUrl);
      }

      return uploadedUrls;
    } catch (error: any) {
      setLoading(false);
      message.error(error.message || '图片上传失败');
      throw error;
    }
  };

  const chatAnswer = async (q: string) => {
    setLoading(true);
    setThinking(1);
    setAgentStatus('正在创建 Adaptive RAG 运行');
    activeRunIdRef.current = '';
    manualAbortRef.current = false;
    activeConversationIdRef.current = conversationId;
    activeNonceRef.current = nonce;

    const imagePaths = await uploadAllImages();

    let token = '';

    const Cap = (await import(`@cap.js/widget`)).default;
    const cap = new Cap({
      apiEndpoint: `${basePath}/share/v1/captcha/`,
    });
    try {
      const solution = await cap.solve();
      token = solution.token;
    } catch (error) {
      setLoading(false);
      setThinking(4);
      message.error('验证失败');
      return;
    }

    const reqData = {
      message: q,
      image_paths: imagePaths,
      attachments: uploadedFiles.map(file => ({
        name: file.name,
        size: file.size,
        type: file.type,
        content: file.content,
      })),
      nonce: '',
      conversation_id: '',
      app_type: 1,
      captcha_token: token,
    };
    if (conversationId) reqData.conversation_id = conversationId;
    if (nonce) reqData.nonce = nonce;

    if (sseClientRef.current) {
      sseClientRef.current.subscribe(
        JSON.stringify(reqData),
        ({ type, content, chunk_result, agent_event }) => {
          if (type === 'run_id') {
            activeRunIdRef.current = content;
          } else if (type === 'agent_event' && agent_event) {
            setAgentStatus(agent_event.message || content);
            setThinking(thinkingStageForAgentEvent(agent_event));
            setConversation(previous => {
              const next = [...previous];
              const latest = next[next.length - 1];
              if (latest) {
                const events = latest.agent_events || [];
                latest.agent_events = appendAgentEvent(events, agent_event);
                if (agent_event.mode) latest.agent_mode = agent_event.mode;
              }
              return next;
            });
          } else if (type === 'conversation_id') {
            activeConversationIdRef.current = content;
            setConversationId(content);
            rememberConversation(historyScope, {
              id: content,
              nonce: activeNonceRef.current,
              title: historyTitleRef.current || q,
              preview: historyPreviewRef.current || q,
              messageCount: historyMessageCountRef.current,
            });
          } else if (type === 'message_id') {
            messageIdRef.current = content;
          } else if (type === 'nonce') {
            activeNonceRef.current = content;
            setNonce(content);
            if (activeConversationIdRef.current) {
              rememberConversation(historyScope, {
                id: activeConversationIdRef.current,
                nonce: content,
                title: historyTitleRef.current || q,
                preview: historyPreviewRef.current || q,
                messageCount: historyMessageCountRef.current,
              });
            }
          } else if (type === 'error') {
            setLoading(false);
            setThinking(4);
            setConversation(prev => {
              const newConversation = [...prev];
              const lastConversation =
                newConversation[newConversation.length - 1];
              if (lastConversation) {
                lastConversation.a =
                  lastConversation.a +
                  (content
                    ? `\n\n回答出现错误：<error>${content}</error>`
                    : '\n\n回答出现错误，请重试');
              }
              return newConversation;
            });
            if (content) message.error(content);
          } else if (type === 'cancelled') {
            setLoading(false);
            setThinking(4);
            setAgentStatus('');
          } else if (type === 'done') {
            setConversation(prev => {
              const newConversation = [...prev];
              const lastConversation =
                newConversation[newConversation.length - 1];
              if (lastConversation) {
                lastConversation.update_time = dayjs().format(
                  'YYYY-MM-DD HH:mm:ss',
                );
                lastConversation.message_id = messageIdRef.current;
                lastConversation.source = 'chat';
              }
              return newConversation;
            });

            setFullAnswer('');
            setLoading(false);
            setAgentStatus('');
            activeRunIdRef.current = '';

            const historyId = activeConversationIdRef.current || conversationId;
            if (historyId) {
              rememberConversation(historyScope, {
                id: historyId,
                nonce: activeNonceRef.current || nonce,
                title: historyTitleRef.current || q,
                preview: historyPreviewRef.current || q,
                messageCount: historyMessageCountRef.current,
              });
            }

            setThinking(4);
          } else if (type === 'data') {
            setFullAnswer(prevFullAnswer => {
              const newFullAnswer = prevFullAnswer + content;

              const { thinkingContent, answerContent } =
                handleThinkingContent(newFullAnswer);

              // 更新状态
              if (newFullAnswer.includes('</think>')) {
                setThinking(3);
              } else if (newFullAnswer.includes('<think>')) {
                setThinking(2);
              } else {
                setThinking(3);
              }
              setConversation(preConversation => {
                const newConversation = [...preConversation];
                const lastConversation =
                  newConversation[newConversation.length - 1];
                if (lastConversation) {
                  lastConversation.a = answerContent;
                  lastConversation.thinking_content = thinkingContent;
                  lastConversation.result_expend = lastResultExpendRef.current;
                  lastConversation.thinking_expend = false;
                }
                return newConversation;
              });

              return newFullAnswer;
            });
          } else if (type === 'chunk_result') {
            const nextReferences = upsertReference(
              latestReferencesRef.current,
              chunk_result,
            );
            latestReferencesRef.current = nextReferences;
            onReferencesChange(nextReferences);
            setConversation(preConversation => {
              const newConversation = [...preConversation];
              const lastConversation =
                newConversation[newConversation.length - 1];
              if (lastConversation) {
                lastConversation.chunk_result = nextReferences;
              }
              return newConversation;
            });
          }
        },
      );
    }
  };

  useEffect(() => {
    // @ts-ignore
    window.CAP_CUSTOM_WASM_URL =
      window.location.origin + `${basePath}/cap@0.0.6/cap_wasm.min.js`;
  }, []);

  const onSearch = (q: string, reset: boolean = false) => {
    if (
      loading ||
      (!q.trim() && uploadedImages.length === 0 && uploadedFiles.length === 0)
    )
      return;
    const question =
      q.trim() ||
      (uploadedFiles.length > 0
        ? '请阅读并总结我上传的文件。'
        : '请分析我上传的图片。');
    if (!conversationId) {
      historyTitleRef.current = question;
    }
    historyPreviewRef.current = question;
    latestReferencesRef.current = [];
    onReferencesChange([]);
    setShouldAutoScroll(true); // 开始新搜索时，重置为自动滚动
    const newConversation = reset
      ? []
      : conversation.some(item => item.source === 'history')
        ? []
        : [...conversation];
    lastResultExpendRef.current = false;
    newConversation.push({
      image_paths: uploadedImages.map(img => img.url),
      q: question,
      a: '',
      score: 0,
      message_id: '',
      update_time: '',
      source: 'chat',
      chunk_result: [],
      thinking_content: '',
      agent_events: [],
      agent_mode: '',
      result_expend: true,
      thinking_expend: true,
      id: uuidv4(),
      attachments: uploadedFiles.map(file => ({
        name: file.name,
        size: file.size,
        type: file.type,
      })),
    });
    historyMessageCountRef.current = newConversation.length;
    messageIdRef.current = '';
    setConversation(newConversation);
    setFullAnswer('');
    setTimeout(() => {
      chatAnswer(question);
      setInput('');
      setUploadedImages([]);
      setUploadedFiles([]);
    }, 0);
  };

  const handleSearchAbort = () => {
    manualAbortRef.current = true;
    const runId = activeRunIdRef.current;
    if (runId) {
      void fetch(`${basePath}/share/v1/chat/cancel`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ run_id: runId }),
        keepalive: true,
      }).catch(() => undefined);
    }
    sseClientRef.current?.unsubscribe();
    activeRunIdRef.current = '';
    setLoading(false);
    setThinking(4);
    setAgentStatus('');
  };

  const isFeedbackEnabled =
    // @ts-ignore
    kbDetail?.settings?.ai_feedback_settings?.is_enabled ?? true;

  const handleScore = async (
    message_id: string,
    score: number,
    type?: string,
    content?: string,
  ) => {
    const data: any = {
      conversation_id: conversationId,
      message_id,
      score,
    };
    if (type) data.type = type;
    if (content) data.feedback_content = content;
    await postShareV1ChatFeedback(data);
    message.success('反馈成功');
    setConversation(
      conversation.map(item => {
        return item.message_id === message_id ? { ...item, score } : item;
      }),
    );
  };

  useEffect(() => {
    sseClientRef.current = new SSEClient({
      url: `${basePath}/share/v1/chat/message`,
      headers: {
        'Content-Type': 'application/json',
      },
      onError: error => {
        setLoading(false);
        setThinking(4);
        if (error instanceof SSEHttpError && error.status === 401) {
          const current = window.location;
          window.location.href = `${basePath}/auth/login?redirect=${encodeURIComponent(current.pathname + current.search)}`;
          return;
        }
        message.error(error.message || '请求失败');
      },
      onCancel: () => {
        setLoading(false);
        setThinking(4);
        if (manualAbortRef.current) {
          manualAbortRef.current = false;
          return;
        }
        setConversation(prev => {
          const newConversation = [...prev];
          const lastConversation = newConversation[newConversation.length - 1];
          if (lastConversation) {
            lastConversation.a =
              lastConversation.a + '\n\n<error>Request canceled</error>';
            lastConversation.update_time = dayjs().format(
              'YYYY-MM-DD HH:mm:ss',
            );
            lastConversation.message_id = messageIdRef.current;
          }
          return newConversation;
        });
      },
    });
    const searchQuery =
      sessionStorage.getItem('chat_search_query') || searchParams.get('ask');
    if (searchQuery) {
      sessionStorage.removeItem('chat_search_query');
      const newSearchParams = new URLSearchParams(searchParams.toString());
      newSearchParams.delete('cid');
      newSearchParams.delete('ask');
      window.history.replaceState(null, '', newSearchParams.toString());
      onSearch(searchQuery, true);
    }
    return () => {
      handleSearchAbort();
      const currentUrl = new URL(window.location.href);
      currentUrl.searchParams.delete('cid');
      currentUrl.searchParams.delete('ask');
      window.history.replaceState(null, '', currentUrl.toString());
      setTimeout(() => {
        onReset();
      });
    };
  }, []);

  useEffect(() => {
    if (conversationId) {
      const currentUrl = new URL(window.location.href);
      currentUrl.searchParams.set('cid', conversationId);
      currentUrl.searchParams.delete('ask');
      window.history.replaceState(null, '', currentUrl.toString());
    }
  }, [conversationId]);

  const loadConversation = async (cid: string, savedNonce = '') => {
    if (!cid) return;
    setLoading(false);
    setThinking(4);
    setFullAnswer('');
    try {
      const res = await getShareV1ConversationDetail({ id: cid });
      const historyConversation: ConversationItem[] = [];
      if (res.messages) {
        let current: Partial<ConversationItem> = { chunk_result: [] };
        res.messages.forEach(item => {
          if (item.role === 'user') {
            current = {
              image_paths: item.image_paths || [],
              q: item.content || '',
              chunk_result: [],
              attachments: ((item as any).info?.attachments || []).map(
                (file: any) => ({
                  name: file.name || '未命名文件',
                  size: file.size || 0,
                  type: file.type || 'text/plain',
                }),
              ),
            };
          } else if (
            item.role === 'assistant' &&
            (current.q ||
              (current.image_paths && current.image_paths.length > 0))
          ) {
            const { thinkingContent, answerContent } = handleThinkingContent(
              item.content || '',
            );
            current.update_time = item.created_at || '';
            current.score = 0;
            current.message_id = '';
            current.thinking_content = thinkingContent;
            current.agent_events = (
              ((item as any).info?.agent_trace || []) as any[]
            ).map(event => ({
              run_id: String((item as any).info?.agent_run_id || ''),
              stage: String(event.stage || ''),
              status: String(event.status || ''),
              message: String(event.message || ''),
              iteration: Number(event.iteration || 0),
              mode: String((item as any).info?.agent_mode || ''),
              queries: [],
              metrics: event.metrics || {},
            }));
            current.agent_mode = String((item as any).info?.agent_mode || '');
            const storedReferences = (
              ((item as any).info?.references || []) as any[]
            ).map(reference => ({
              node_id: String(reference.node_id || ''),
              node_release_id: String(reference.node_release_id || ''),
              source_version: String(reference.source_version || ''),
              knowledge_version_id: String(
                reference.knowledge_version_id || '',
              ),
              knowledge_version: reference.knowledge_version || '',
              name: String(reference.name || '未命名文档'),
              summary: String(reference.summary || ''),
              url: String(reference.url || ''),
              emoji: String(reference.emoji || ''),
            }));
            const cited = reconcileCitedReferences(
              answerContent,
              storedReferences,
            );
            current.a = cited.answer;
            current.chunk_result = cited.references;
            current.source = 'history';
            current.id = uuidv4();
            current.result_expend = false;
            current.thinking_expend = false;
            historyConversation.push(current as ConversationItem);
            current = {};
          }
        });
        if (
          current.q ||
          (current.image_paths && current.image_paths.length > 0)
        ) {
          historyConversation.push({
            image_paths: current.image_paths || [],
            q: current.q || '',
            a: '',
            score: 0,
            update_time: '',
            message_id: '',
            source: 'history',
            chunk_result: [],
            thinking_content: '',
            agent_events: [],
            agent_mode: '',
            id: uuidv4(),
            result_expend: true,
            thinking_expend: true,
            attachments: current.attachments,
          });
        }
      }

      const fallbackReferences = (((res as any).references || []) as any[]).map(
        reference => ({
          node_id: String(reference.node_id || ''),
          node_release_id: String(reference.node_release_id || ''),
          knowledge_version_id: String(reference.knowledge_version_id || ''),
          name: String(reference.name || '未命名文档'),
          summary: String(reference.summary || ''),
        }),
      );
      const latestConversation =
        historyConversation[historyConversation.length - 1];
      if (
        latestConversation &&
        latestConversation.chunk_result.length === 0 &&
        fallbackReferences.length > 0
      ) {
        const cited = reconcileCitedReferences(
          latestConversation.a,
          fallbackReferences,
        );
        latestConversation.a = cited.answer;
        latestConversation.chunk_result = cited.references;
      }
      const latestReferences = latestConversation?.chunk_result || [];
      latestReferencesRef.current = latestReferences;
      onReferencesChange(latestReferences);

      const title =
        res.subject || historyConversation[0]?.q || '继续上次的问答';
      const preview =
        historyConversation[historyConversation.length - 1]?.q || title;
      activeConversationIdRef.current = cid;
      activeNonceRef.current = savedNonce;
      historyTitleRef.current = title;
      historyPreviewRef.current = preview;
      historyMessageCountRef.current = Math.max(historyConversation.length, 1);
      setConversationId(cid);
      setNonce(savedNonce);
      setConversation(historyConversation);
      setShouldAutoScroll(false);
      rememberConversation(historyScope, {
        id: cid,
        nonce: savedNonce,
        title,
        preview,
        messageCount: Math.max(historyConversation.length, 1),
        updatedAt:
          historyConversation[historyConversation.length - 1]?.update_time ||
          res.created_at,
      });
    } catch (error: any) {
      message.error(error?.message || '历史消息加载失败');
    }
  };

  useEffect(() => {
    const cid = searchParams.get('cid');
    if (cid) {
      const savedHistory = readConversationHistory(historyScope).find(
        item => item.id === cid,
      );
      loadConversation(cid, savedHistory?.nonce || '');
    }
  }, []);

  useEffect(() => {
    if (!qaModalOpen || !qaConversationTarget?.id) return;
    loadConversation(qaConversationTarget.id, qaConversationTarget.nonce);
    setQaConversationTarget?.(undefined);
  }, [qaModalOpen, qaConversationTarget?.id]);

  useEffect(() => {
    if (!qaModalOpen) {
      conversation.forEach(item => {
        item.image_paths.forEach(image => {
          if (image.startsWith('blob:')) {
            URL.revokeObjectURL(image);
          }
        });
      });
    }
  }, [qaModalOpen, conversation]);

  return (
    <StyledMainContainer className={palette.mode === 'dark' ? 'md-dark' : ''}>
      {/* 无对话时显示欢迎界面 */}
      {conversation.length === 0 && (
        <Box
          sx={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'flex-start',
            justifyContent: 'flex-start',
            pt: { xs: 5, sm: 7 },
            px: { xs: 0.5, sm: 1 },
          }}
        >
          <Stack
            direction='row'
            justifyContent='flex-end'
            sx={{ width: '100%' }}
          >
            <Box
              sx={{
                px: 1.6,
                py: 1,
                borderRadius: '10px',
                bgcolor: '#f3f3f3',
                color: '#222',
                fontSize: 13,
              }}
            >
              你好
            </Box>
          </Stack>
          <Typography
            sx={{
              mt: 6,
              color: '#b0b2b7',
              fontSize: 12,
            }}
          >
            › 准备完成
          </Typography>
          <Typography sx={{ mt: 2.5, fontSize: 15, fontWeight: 650 }}>
            有什么想了解的，告诉我。
          </Typography>
          <Typography sx={{ mt: 3, color: '#b0b2b7', fontSize: 12 }}>
            猜你想问
          </Typography>
          <Stack gap={1} sx={{ mt: 1.3, alignItems: 'flex-start' }}>
            {(hotSearch.length > 0
              ? hotSearch.slice(0, 3)
              : ['这份知识库包含什么？', '推荐几个值得学习的 Agent 项目']
            ).map(suggestion => (
              <Button
                key={suggestion}
                variant='outlined'
                onClick={() => onSuggestionClick(suggestion)}
                sx={{
                  minHeight: 36,
                  px: 1.5,
                  color: '#777b82',
                  borderColor: '#e8e8e8',
                  borderRadius: '9px',
                  bgcolor: '#fafafa',
                  fontSize: 12,
                  fontWeight: 400,
                  boxShadow: 'none',
                }}
              >
                {suggestion}
              </Button>
            ))}
          </Stack>
        </Box>
      )}

      {/* 有对话时显示对话历史 */}
      <StyledConversationContainer
        direction='column'
        className='conversation-container'
        sx={{
          mb: conversation?.length > 0 ? 2 : 0,
          display: conversation.length > 0 ? 'flex' : 'none',
        }}
      >
        <Stack gap={2}>
          {conversation.map((item, index) => (
            <StyledConversationItem key={item.id}>
              {item.image_paths.length > 0 && (
                <ImagePreview.PreviewGroup>
                  <Stack direction='row' gap={1} sx={{ alignSelf: 'flex-end' }}>
                    {item.image_paths.map((url: string) => (
                      <ImagePreview
                        alt={url}
                        key={url}
                        src={getImagePath(url, basePath)}
                        width={100}
                        height={100}
                        style={{
                          borderRadius: '10px',
                          objectFit: 'cover',
                          cursor: 'pointer',
                        }}
                        referrerPolicy='no-referrer'
                      />
                    ))}
                  </Stack>
                </ImagePreview.PreviewGroup>
              )}

              {item.attachments && item.attachments.length > 0 && (
                <Stack
                  direction='row'
                  flexWrap='wrap'
                  gap={0.75}
                  justifyContent='flex-end'
                >
                  {item.attachments.map(file => (
                    <Stack
                      key={`${item.id}-${file.name}`}
                      direction='row'
                      alignItems='center'
                      gap={0.75}
                      sx={{
                        px: 1.25,
                        py: 0.75,
                        borderRadius: '10px',
                        bgcolor: 'action.hover',
                        border: '1px solid',
                        borderColor: 'divider',
                      }}
                    >
                      <AttachFileRounded sx={{ fontSize: 15 }} />
                      <Box>
                        <Typography sx={{ fontSize: 12, fontWeight: 600 }}>
                          {file.name}
                        </Typography>
                        <Typography
                          sx={{ fontSize: 10, color: 'text.disabled' }}
                        >
                          {formatAttachmentSize(file.size)}
                        </Typography>
                      </Box>
                    </Stack>
                  ))}
                </Stack>
              )}

              {/* 用户问题气泡 - 右对齐 */}
              {item.q && <StyledUserBubble>{item.q}</StyledUserBubble>}
              {/* AI回答气泡 - 左对齐 */}
              <StyledAiBubble>
                <AgentTrace
                  events={item.agent_events || []}
                  mode={item.agent_mode}
                  active={index === conversation.length - 1 && loading}
                />
                {/* 搜索结果 */}
                {item.chunk_result.length > 0 && (
                  <StyledChunkAccordion
                    expanded={item.result_expend}
                    onChange={(event, expanded) => {
                      setConversation(prev => {
                        const newConversation = [...prev];
                        if (index === conversation.length - 1) {
                          lastResultExpendRef.current = expanded;
                        }
                        newConversation[index].result_expend = expanded;
                        return newConversation;
                      });
                    }}
                  >
                    <StyledChunkAccordionSummary
                      expandIcon={<ExpandMoreIcon sx={{ fontSize: 16 }} />}
                    >
                      <Typography
                        variant='body2'
                        sx={theme => ({
                          fontSize: 12,
                          color: alpha(theme.palette.text.primary, 0.5),
                        })}
                      >
                        共找到 {item.chunk_result.length} 个结果
                      </Typography>
                    </StyledChunkAccordionSummary>

                    <StyledChunkAccordionDetails>
                      <Stack gap={1} alignItems='flex-start'>
                        {item.chunk_result.map((chunk, chunkIndex) => (
                          <StyledChunkItem key={chunkIndex}>
                            <Typography
                              variant='body2'
                              className='hover-primary'
                              sx={theme => ({
                                fontSize: 12,
                                color: alpha(theme.palette.text.primary, 0.5),
                              })}
                              onClick={() => {
                                onReferenceSelect(chunk, item.chunk_result);
                              }}
                            >
                              [文档 {chunkIndex + 1}] {chunk.name}
                            </Typography>
                          </StyledChunkItem>
                        ))}
                      </Stack>
                    </StyledChunkAccordionDetails>
                  </StyledChunkAccordion>
                )}

                {/* 加载状态 */}
                {index === conversation.length - 1 && loading && (
                  <LoadingContent thinking={thinking} status={agentStatus} />
                )}

                {/* 思考过程 */}
                {!!item.thinking_content && (
                  <StyledThinkingAccordion
                    expanded={item.thinking_expend}
                    onChange={(event, expanded) => {
                      setConversation(prev => {
                        const newConversation = [...prev];
                        newConversation[index].thinking_expend = expanded;
                        return newConversation;
                      });
                    }}
                  >
                    <StyledThinkingAccordionSummary
                      expandIcon={<ExpandMoreIcon sx={{ fontSize: 16 }} />}
                    >
                      <Stack direction='row' alignItems='center' gap={1}>
                        {thinking === 2 &&
                          index === conversation.length - 1 && (
                            <Image
                              src={aiLoading}
                              alt='ai-loading'
                              width={20}
                              height={20}
                            />
                          )}

                        <Typography
                          variant='body2'
                          sx={theme => ({
                            fontSize: 12,
                            color: alpha(theme.palette.text.primary, 0.5),
                          })}
                        >
                          {thinking === 2 && index === conversation.length - 1
                            ? '思考中...'
                            : '已思考'}
                        </Typography>
                      </Stack>
                    </StyledThinkingAccordionSummary>

                    <StyledThinkingAccordionDetails>
                      <MarkDown2
                        content={item.thinking_content || ''}
                        autoScroll={false}
                      />
                    </StyledThinkingAccordionDetails>
                  </StyledThinkingAccordion>
                )}

                {/* AI回答内容 */}
                <StyledAiBubbleContent>
                  <MarkDown2
                    content={item.a}
                    autoScroll={false}
                    references={item.chunk_result}
                    activeReferenceId={activeReferenceId}
                    onReferenceClick={reference =>
                      onReferenceSelect(reference, item.chunk_result)
                    }
                  />
                </StyledAiBubbleContent>

                {/* 操作按钮 */}
                {(index !== conversation.length - 1 || !loading) && (
                  <StyledActionStack
                    direction={mobile ? 'column' : 'row'}
                    alignItems={mobile ? 'flex-start' : 'center'}
                    justifyContent='space-between'
                    gap={mobile ? 1 : 3}
                  >
                    <Stack direction='row' gap={3} alignItems='center'>
                      <span>生成于 {dayjs(item.update_time).fromNow()}</span>

                      <IconCopy
                        sx={{ cursor: 'pointer' }}
                        onClick={() => {
                          copyText(item.a);
                        }}
                      />

                      {isFeedbackEnabled && item.source === 'chat' && (
                        <>
                          {item.score === 1 && (
                            <IconDianzanXuanzhong1 sx={{ cursor: 'pointer' }} />
                          )}
                          {item.score !== 1 && (
                            <IconDianzanWeixuanzhong
                              sx={{ cursor: 'pointer' }}
                              onClick={() => {
                                if (item.score === 0)
                                  handleScore(item.message_id, 1);
                              }}
                            />
                          )}
                          {item.score !== -1 && (
                            <IconDiancaiWeixuanzhong
                              sx={{ cursor: 'pointer' }}
                              onClick={() => {
                                if (item.score === 0) {
                                  setConversationItem(item);
                                  setOpen(true);
                                }
                              }}
                            />
                          )}
                          {item.score === -1 && (
                            <IconADiancaiWeixuanzhong2
                              sx={{ cursor: 'pointer' }}
                            />
                          )}
                        </>
                      )}
                    </Stack>
                    <Box>
                      {kbDetail?.settings?.disclaimer_settings?.content}
                    </Box>
                  </StyledActionStack>
                )}
              </StyledAiBubble>
            </StyledConversationItem>
          ))}
        </Stack>
      </StyledConversationContainer>
      {conversation.length > 0 && (
        <Button
          variant='contained'
          sx={theme => ({
            textTransform: 'none',
            minWidth: 'auto',
            px: 3.5,
            py: '2px',
            gap: 0.5,
            fontSize: 12,
            backgroundColor: 'background.default',
            color: 'text.primary',
            boxShadow: `0px 1px 2px 0px ${alpha(theme.palette.text.primary, 0.06)}`,
            border: '1px solid',
            borderColor: alpha(theme.palette.text.primary, 0.1),
            cursor: 'pointer',
            '&:hover': {
              boxShadow: `0px 1px 2px 0px ${alpha(theme.palette.text.primary, 0.06)}`,
              borderColor: 'primary.main',
              color: 'primary.main',
            },
            mb: 2,
            display: 'none',
          })}
          onClick={onReset}
        >
          <IconXinduihua sx={{ fontSize: 14 }} />
          新会话
        </Button>
      )}

      <StyledInputContainer>
        <StyledInputWrapper>
          {/* 多张图片预览 */}
          {uploadedImages.length > 0 && (
            <StyledImagePreviewStack direction='row' flexWrap='wrap' gap={1}>
              {uploadedImages.map(image => (
                <StyledImagePreviewItem key={image.id}>
                  <Image
                    src={image.url}
                    alt='uploaded'
                    width={40}
                    height={40}
                    style={{
                      objectFit: 'cover',
                    }}
                  />
                  <StyledImageRemoveButton
                    size='small'
                    onClick={() => handleRemoveImage(image.id)}
                  >
                    <CloseIcon sx={{ fontSize: 10 }} />
                  </StyledImageRemoveButton>
                </StyledImagePreviewItem>
              ))}
            </StyledImagePreviewStack>
          )}
          {uploadedFiles.length > 0 && (
            <Stack direction='row' flexWrap='wrap' gap={0.75} sx={{ mb: 0.5 }}>
              {uploadedFiles.map(file => (
                <Stack
                  key={file.id}
                  direction='row'
                  alignItems='center'
                  gap={0.75}
                  sx={{
                    pl: 1,
                    pr: 0.5,
                    py: 0.5,
                    borderRadius: '9px',
                    bgcolor: 'action.hover',
                    border: '1px solid',
                    borderColor: 'divider',
                  }}
                >
                  <AttachFileRounded sx={{ fontSize: 15 }} />
                  <Box sx={{ maxWidth: 220 }}>
                    <Typography noWrap sx={{ fontSize: 11.5, fontWeight: 600 }}>
                      {file.name}
                    </Typography>
                    <Typography sx={{ fontSize: 9.5, color: 'text.disabled' }}>
                      {formatAttachmentSize(file.size)}
                    </Typography>
                  </Box>
                  <IconButton
                    size='small'
                    aria-label={`移除文件 ${file.name}`}
                    onClick={() => handleRemoveFile(file.id)}
                    sx={{ width: 22, height: 22 }}
                  >
                    <CloseIcon sx={{ fontSize: 12 }} />
                  </IconButton>
                </Stack>
              ))}
            </Stack>
          )}
          <StyledTextField
            fullWidth
            multiline
            rows={2}
            disabled={loading}
            ref={inputRef}
            size='small'
            value={input}
            onChange={handleInputChange}
            onFocus={handleInputFocus}
            onBlur={handleInputBlur}
            onPaste={handlePaste}
            onKeyDown={e => {
              const isComposing =
                e.nativeEvent.isComposing || e.nativeEvent.keyCode === 229;
              if (
                e.key === 'Enter' &&
                !e.shiftKey &&
                (input.length > 0 ||
                  uploadedImages.length > 0 ||
                  uploadedFiles.length > 0) &&
                !isComposing
              ) {
                e.preventDefault();
                handleSearch();
              }
            }}
            placeholder={placeholder}
            autoComplete='off'
          />
          <StyledActionButtonStack
            direction='row'
            alignItems='center'
            justifyContent='space-between'
          >
            <Stack direction='row' alignItems='center' gap={0.5}>
              <input
                ref={fileInputRef}
                type='file'
                accept='.jpg,.jpeg,.png,.gif,.webp'
                multiple
                style={{ display: 'none' }}
                onChange={handleImageUpload}
              />
              <input
                ref={documentInputRef}
                type='file'
                accept='.txt,.md,.csv,.json,.html,.xml,.yaml,.yml,.log'
                multiple
                style={{ display: 'none' }}
                onChange={handleDocumentUpload}
              />
              <IconButton
                aria-label='上传图片'
                size='small'
                onClick={() => fileInputRef.current?.click()}
                disabled={loading}
                sx={{ flexShrink: 0 }}
              >
                <IconTupian sx={{ fontSize: 20, color: 'text.secondary' }} />
              </IconButton>
              <IconButton
                aria-label='上传文件'
                size='small'
                onClick={() => documentInputRef.current?.click()}
                disabled={loading}
                sx={{ flexShrink: 0 }}
              >
                <AttachFileRounded
                  sx={{ fontSize: 19, color: 'text.secondary' }}
                />
              </IconButton>
            </Stack>

            <Box
              sx={{
                fontSize: 12,
                flexShrink: 0,
                cursor: 'pointer',
              }}
            >
              {loading ? (
                <ChatLoading
                  thinking={thinking}
                  onClick={() => {
                    setThinking(4);
                    handleSearchAbort();
                  }}
                />
              ) : (
                <IconButton
                  size='small'
                  disabled={
                    input.length === 0 &&
                    uploadedImages.length === 0 &&
                    uploadedFiles.length === 0
                  }
                  onClick={() => {
                    if (
                      input.length > 0 ||
                      uploadedImages.length > 0 ||
                      uploadedFiles.length > 0
                    ) {
                      handleSearchAbort();
                      setThinking(1);
                      handleSearch();
                    }
                  }}
                >
                  <IconFasong
                    sx={{
                      fontSize: 16,
                      color:
                        input.length > 0 ||
                        uploadedImages.length > 0 ||
                        uploadedFiles.length > 0
                          ? 'primary.main'
                          : 'text.disabled',
                    }}
                  />
                </IconButton>
              )}
            </Box>
          </StyledActionButtonStack>
        </StyledInputWrapper>
      </StyledInputContainer>
      {/* 模糊搜索建议列表 */}
      {showFuzzySuggestions &&
        fuzzySuggestions.length > 0 &&
        conversation.length === 0 && (
          <StyledFuzzySuggestionsStack gap={0.5}>
            {fuzzySuggestions.map((suggestion, index) => (
              <StyledFuzzySuggestionItem
                key={index}
                onClick={() => handleFuzzySuggestionClick(suggestion)}
              >
                {highlightMatch(suggestion, input)}
              </StyledFuzzySuggestionItem>
            ))}
          </StyledFuzzySuggestionsStack>
        )}

      <Feedback
        open={open}
        onClose={() => setOpen(false)}
        onSubmit={handleScore}
        data={conversationItem}
      />
    </StyledMainContainer>
  );
};

export default AiQaContent;
