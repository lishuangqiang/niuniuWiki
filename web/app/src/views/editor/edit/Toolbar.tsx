'use client';
import { EditorToolbar, UseTiptapReturn } from '@ctzhian/tiptap';
import { Box } from '@mui/material';

interface ToolbarProps {
  editorRef: UseTiptapReturn;
  handleAiGenerate?: () => void;
}

const Toolbar = ({ editorRef, handleAiGenerate }: ToolbarProps) => {
  return (
    <Box
      sx={{
        width: 'auto',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: '16px',
        bgcolor: 'rgba(var(--mui-palette-background-paperChannel) / .76)',
        backdropFilter: 'saturate(180%) blur(20px)',
        boxShadow: '0 8px 26px rgba(0,0,0,.055)',
        px: 0.5,
        mx: { xs: 1, md: 2 },
        mb: 1.25,
      }}
    >
      {editorRef.editor && <EditorToolbar editor={editorRef.editor} />}
    </Box>
  );
};

export default Toolbar;
