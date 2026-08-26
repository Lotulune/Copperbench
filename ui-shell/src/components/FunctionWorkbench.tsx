import React, { useState, useEffect, useMemo } from 'react';
import {
  ArrowLeft,
  FileCode2,
  Save,
  Check,
  X,
  Plus,
  Trash2,
  AlertTriangle,
  Code2,
  Tags,
  FolderTree,
  Sparkles,
  RotateCcw
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ModElementSummary, FieldChange } from '../types/contract';
import { t } from '../i18n';

interface FunctionWorkbenchProps {
  element: ModElementSummary;
  onClose: () => void;
}

type FunctionTab = 'editor' | 'tags' | 'preview';

const SNIPPETS: Array<{ label: string; snippet: string; description: string }> = [
  {
    label: 'execute as @a',
    snippet: 'execute as @a at @s run ',
    description: '针对所有在线玩家执行后续命令'
  },
  {
    label: 'title 显示标题',
    snippet: 'title @a title {"text":"欢迎来到冒险世界！","color":"gold","bold":true}',
    description: '在屏幕中央向玩家显示全屏大标题'
  },
  {
    label: 'give 给予物品',
    snippet: 'give @p minecraft:diamond 1',
    description: '向最近的玩家发放指定数量的物品'
  },
  {
    label: 'particle 生成粒子',
    snippet: 'particle minecraft:happy_villager ~ ~1 ~ 0.5 0.5 0.5 0.1 20',
    description: '在当前坐标周围生成粒子视觉效果'
  },
  {
    label: 'scoreboard 计分板',
    snippet: 'scoreboard players add @s points 1',
    description: '为当前实体的计分项增加数值'
  },
  {
    label: 'tag 标签操作',
    snippet: 'tag @s add quest_completed',
    description: '为实体添加自定义标识标签'
  },
  {
    label: '# 注释说明',
    snippet: '# 这是函数逻辑的注释说明',
    description: '添加说明注释（以 # 开头）'
  }
];

const DEFAULT_CODE = `# 函数: 初始化事件
# 在加载或触发时按顺序执行以下命令
tellraw @a {"text":"[Copperbench] 函数已触发","color":"aqua"}
particle minecraft:totem_of_undying ~ ~1 ~ 0.5 0.5 0.5 0.2 30
`;

export const FunctionWorkbench: React.FC<FunctionWorkbenchProps> = ({ element, onClose }) => {
  const { updateModElement, getModElementEditor } = useWorkbench();

  const [activeTab, setActiveTab] = useState<FunctionTab>('editor');
  const [code, setCode] = useState<string>(DEFAULT_CODE);
  const [tags, setTags] = useState<string[]>(['minecraft:load']);
  const [namespace, setNamespace] = useState<string>('copperbench');
  const [newTag, setNewTag] = useState<string>('');
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isDirty, setIsDirty] = useState(false);

  // Load initial field values from editor projection if available
  useEffect(() => {
    let cancelled = false;
    getModElementEditor(element.id).then((projection) => {
      if (cancelled || !projection) return;
      const allFields = projection.sections.flatMap((s) => s.fields);
      const codeField = allFields.find((f) => f.path === '/fields/code');
      const tagsField = allFields.find((f) => f.path === '/fields/tags');
      const nsField = allFields.find((f) => f.path === '/fields/namespace');

      if (codeField && typeof codeField.value === 'string') {
        setCode(codeField.value);
      }
      if (tagsField && Array.isArray(tagsField.value)) {
        setTags(tagsField.value as string[]);
      }
      if (nsField && typeof nsField.value === 'string') {
        setNamespace(nsField.value);
      }
      setIsDirty(false);
    }).catch(() => {
      // Fallback to default
    });
    return () => {
      cancelled = true;
    };
  }, [element.id, getModElementEditor]);

  // Linting: check for lines starting with '/' or empty command errors
  const lineDiagnostics = useMemo(() => {
    const lines = code.split('\n');
    const diagnostics: Array<{ line: number; message: string; severity: 'warning' | 'error' }> = [];

    lines.forEach((rawLine, index) => {
      const trimmed = rawLine.trim();
      if (!trimmed || trimmed.startsWith('#')) return;

      if (trimmed.startsWith('/')) {
        diagnostics.push({
          line: index + 1,
          message: `第 ${index + 1} 行：Minecraft 函数文件中的命令不应包含开头的 '/'。`,
          severity: 'warning'
        });
      }
    });

    return diagnostics;
  }, [code]);

  const commandCount = useMemo(() => {
    return code
      .split('\n')
      .map((l) => l.trim())
      .filter((l) => l.length > 0 && !l.startsWith('#')).length;
  }, [code]);

  const lineCount = useMemo(() => {
    return code.split('\n').length;
  }, [code]);

  const handleCodeChange = (newText: string) => {
    setCode(newText);
    setIsDirty(true);
    setSaveSuccess(false);
  };

  const handleInsertSnippet = (snippetText: string) => {
    setCode((prev) => {
      const endsWithNewline = prev.endsWith('\n') || prev === '';
      const updated = prev + (endsWithNewline ? '' : '\n') + snippetText + '\n';
      return updated;
    });
    setIsDirty(true);
  };

  const handleStripLeadingSlashes = () => {
    const lines = code.split('\n');
    let fixCount = 0;
    const fixed = lines.map((line) => {
      const trimmed = line.trimStart();
      if (!trimmed.startsWith('#') && trimmed.startsWith('/')) {
        fixCount++;
        const leadingWhitespace = line.slice(0, line.length - trimmed.length);
        return leadingWhitespace + trimmed.slice(1);
      }
      return line;
    });
    setCode(fixed.join('\n'));
    setIsDirty(true);
    setMessage(`已自动移除 ${fixCount} 处命令开头的 '/' 斜杠。`);
  };

  const handleAddTag = () => {
    const tag = newTag.trim();
    if (!tag) return;
    if (!tags.includes(tag)) {
      setTags([...tags, tag]);
      setIsDirty(true);
    }
    setNewTag('');
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter((t) => t !== tagToRemove));
    setIsDirty(true);
  };

  const handleSave = async () => {
    setIsSaving(true);
    setMessage(null);
    setSaveSuccess(false);

    const changes: FieldChange[] = [
      { path: '/fields/code', value: code },
      { path: '/fields/tags', value: tags },
      { path: '/fields/namespace', value: namespace }
    ];

    try {
      const result = await updateModElement(element.id, changes);
      setIsSaving(false);
      if (result.status === 'committed') {
        setSaveSuccess(true);
        setIsDirty(false);
        setTimeout(() => setSaveSuccess(false), 2500);
      } else {
        setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '保存函数失败。');
      }
    } catch {
      setIsSaving(false);
      setMessage('保存函数时发生网络或宿主错误。');
    }
  };

  return (
    <div
      className="function-workbench animate-fade-in"
      data-testid="function-workbench"
      style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        overflow: 'hidden',
        background: 'var(--bg-base)'
      }}
    >
      {/* Top Toolbar */}
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 18px',
          borderBottom: '1px solid var(--border-subtle)',
          background: 'var(--bg-surface)',
          gap: '12px',
          flexWrap: 'wrap'
        }}
      >
        {/* Left: Back + Identity */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <button
            type="button"
            className="btn-secondary"
            onClick={onClose}
            aria-label="返回元素列表"
            data-testid="function-back-btn"
            style={{ padding: '5px 10px', fontSize: '12px' }}
          >
            <ArrowLeft size={14} />
            <span>返回</span>
          </button>

          <div
            style={{
              width: '32px',
              height: '32px',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--badge-blue-bg)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--badge-blue)'
            }}
          >
            <FileCode2 size={18} />
          </div>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontWeight: 700, fontSize: '14px', color: 'var(--text-main)' }}>
                {element.displayName}
              </span>
              <span className="badge badge-copper">FUNCTION</span>
              <span
                className={`badge badge-${element.state === 'valid' ? 'green' : 'amber'}`}
              >
                {element.state.toUpperCase()}
              </span>
              {isDirty && (
                <span className="badge badge-amber" data-testid="function-dirty-badge">
                  未保存更改
                </span>
              )}
            </div>
            <div
              style={{
                fontSize: '11px',
                color: 'var(--text-sub)',
                fontFamily: 'var(--font-mono)'
              }}
            >
              {namespace}:{element.name}.mcfunction
            </div>
          </div>
        </div>

        {/* Center: Tabs */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            background: 'var(--bg-panel)',
            padding: '3px',
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--border-subtle)',
            gap: '4px'
          }}
        >
          <button
            type="button"
            onClick={() => setActiveTab('editor')}
            aria-pressed={activeTab === 'editor'}
            data-testid="function-tab-editor"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              fontSize: '11px',
              fontWeight: activeTab === 'editor' ? 600 : 500,
              borderRadius: 'var(--radius-xs)',
              background: activeTab === 'editor' ? 'var(--accent-copper)' : 'transparent',
              color: activeTab === 'editor' ? '#ffffff' : 'var(--text-muted)'
            }}
          >
            <Code2 size={13} />
            <span>命令编辑器</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('tags')}
            aria-pressed={activeTab === 'tags'}
            data-testid="function-tab-tags"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              fontSize: '11px',
              fontWeight: activeTab === 'tags' ? 600 : 500,
              borderRadius: 'var(--radius-xs)',
              background: activeTab === 'tags' ? 'var(--accent-copper)' : 'transparent',
              color: activeTab === 'tags' ? '#ffffff' : 'var(--text-muted)'
            }}
          >
            <Tags size={13} />
            <span>函数标签 ({tags.length})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('preview')}
            aria-pressed={activeTab === 'preview'}
            data-testid="function-tab-preview"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              fontSize: '11px',
              fontWeight: activeTab === 'preview' ? 600 : 500,
              borderRadius: 'var(--radius-xs)',
              background: activeTab === 'preview' ? 'var(--accent-copper)' : 'transparent',
              color: activeTab === 'preview' ? '#ffffff' : 'var(--text-muted)'
            }}
          >
            <FolderTree size={13} />
            <span>数据包结构预览</span>
          </button>
        </div>

        {/* Right: Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {lineDiagnostics.length > 0 && (
            <button
              type="button"
              className="btn-secondary"
              onClick={handleStripLeadingSlashes}
              title="自动移除命令开头的斜杠 /"
              data-testid="function-clean-slashes-btn"
              style={{ fontSize: '11px', color: 'var(--badge-amber)' }}
            >
              <RotateCcw size={13} />
              <span>清理斜杠 ({lineDiagnostics.length})</span>
            </button>
          )}

          {saveSuccess && (
            <span
              style={{
                color: 'var(--badge-green)',
                fontSize: '11px',
                display: 'flex',
                alignItems: 'center',
                gap: '4px'
              }}
            >
              <Check size={14} /> 已保存
            </span>
          )}

          <button
            type="button"
            className="btn-primary"
            onClick={handleSave}
            disabled={isSaving}
            data-testid="function-save-btn"
            style={{ fontSize: '12px', minWidth: '90px' }}
          >
            <Save size={14} />
            <span>{isSaving ? '保存中…' : '保存函数'}</span>
          </button>
        </div>
      </header>

      {/* Notification / Alert Bar */}
      {message && (
        <div
          role="status"
          style={{
            padding: '8px 18px',
            background: 'var(--badge-blue-bg)',
            borderBottom: '1px solid rgba(88, 166, 255, 0.3)',
            color: 'var(--badge-blue)',
            fontSize: '11px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}
        >
          <span>{message}</span>
          <button
            type="button"
            onClick={() => setMessage(null)}
            style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer' }}
          >
            <X size={13} />
          </button>
        </div>
      )}

      {/* Main Content Body */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', position: 'relative' }}>
        {activeTab === 'editor' && (
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            {/* Snippets Bar */}
            <div
              style={{
                padding: '8px 16px',
                background: 'var(--bg-surface)',
                borderBottom: '1px solid var(--border-subtle)',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                overflowX: 'auto'
              }}
            >
              <span
                style={{
                  fontSize: '11px',
                  fontWeight: 600,
                  color: 'var(--text-sub)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                  flexShrink: 0
                }}
              >
                <Sparkles size={13} />
                快速插入片段：
              </span>
              {SNIPPETS.map((snip) => (
                <button
                  key={snip.label}
                  type="button"
                  onClick={() => handleInsertSnippet(snip.snippet)}
                  title={snip.description}
                  data-testid={`snippet-${snip.label.split(' ')[0]}`}
                  style={{
                    padding: '3px 8px',
                    fontSize: '11px',
                    borderRadius: 'var(--radius-xs)',
                    border: '1px solid var(--border-subtle)',
                    background: 'var(--bg-panel)',
                    color: 'var(--text-main)',
                    cursor: 'pointer',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {snip.label}
                </button>
              ))}
            </div>

            {/* Code Textarea & Gutter */}
            <div style={{ flex: 1, display: 'flex', overflow: 'hidden', position: 'relative' }}>
              {/* Line Numbers Gutter */}
              <div
                style={{
                  width: '48px',
                  background: 'var(--bg-panel)',
                  borderRight: '1px solid var(--border-subtle)',
                  padding: '12px 6px',
                  textAlign: 'right',
                  color: 'var(--text-sub)',
                  fontFamily: 'var(--font-mono)',
                  fontSize: '12px',
                  lineHeight: '20px',
                  userSelect: 'none',
                  overflow: 'hidden'
                }}
              >
                {Array.from({ length: lineCount }).map((_, idx) => {
                  const lineNum = idx + 1;
                  const hasDiag = lineDiagnostics.some((d) => d.line === lineNum);
                  return (
                    <div
                      key={lineNum}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'flex-end',
                        gap: '4px',
                        color: hasDiag ? 'var(--badge-amber)' : 'inherit',
                        fontWeight: hasDiag ? 700 : 400
                      }}
                    >
                      {hasDiag && <AlertTriangle size={10} />}
                      <span>{lineNum}</span>
                    </div>
                  );
                })}
              </div>

              {/* Text Area */}
              <textarea
                value={code}
                onChange={(e) => handleCodeChange(e.target.value)}
                placeholder="# 输入 Minecraft 命令（每行一条，支持 # 注释）..."
                spellCheck={false}
                data-testid="function-code-editor"
                style={{
                  flex: 1,
                  height: '100%',
                  resize: 'none',
                  border: 'none',
                  outline: 'none',
                  padding: '12px',
                  background: 'var(--bg-base)',
                  color: 'var(--text-main)',
                  fontFamily: 'var(--font-mono)',
                  fontSize: '12px',
                  lineHeight: '20px',
                  whiteSpace: 'pre',
                  overflowY: 'auto'
                }}
              />
            </div>

            {/* Bottom Status Bar & Lint Diagnostics */}
            <footer
              style={{
                padding: '8px 16px',
                background: 'var(--bg-surface)',
                borderTop: '1px solid var(--border-subtle)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                fontSize: '11px',
                color: 'var(--text-sub)'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <span>总行数：{lineCount}</span>
                <span>有效命令数：{commandCount}</span>
                {lineDiagnostics.length > 0 ? (
                  <span
                    style={{
                      color: 'var(--badge-amber)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      fontWeight: 600
                    }}
                    data-testid="function-lint-warnings"
                  >
                    <AlertTriangle size={13} /> {lineDiagnostics.length} 处命令格式提示
                  </span>
                ) : (
                  <span
                    style={{
                      color: 'var(--badge-green)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px'
                    }}
                  >
                    <Check size={13} /> 语法检查通过
                  </span>
                )}
              </div>

              <div>
                <span>命名空间：</span>
                <input
                  type="text"
                  value={namespace}
                  onChange={(e) => {
                    setNamespace(e.target.value);
                    setIsDirty(true);
                  }}
                  data-testid="function-namespace-input"
                  style={{
                    padding: '2px 6px',
                    fontSize: '11px',
                    width: '120px',
                    marginLeft: '4px',
                    fontFamily: 'var(--font-mono)'
                  }}
                />
              </div>
            </footer>
          </div>
        )}

        {activeTab === 'tags' && (
          <div
            style={{
              flex: 1,
              padding: '24px',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: '20px',
              maxWidth: '720px'
            }}
          >
            <div>
              <h2 style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text-main)' }}>
                函数标签 (Function Tags)
              </h2>
              <p style={{ fontSize: '12px', color: 'var(--text-sub)', marginTop: '4px' }}>
                将此函数注册到特定标签中，以便在游戏循环（#minecraft:tick）或世界加载（#minecraft:load）时自动调用。
              </p>
            </div>

            {/* Quick Presets */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-sub)' }}>
                常用标准标签：
              </span>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {[
                  { tag: 'minecraft:load', desc: '世界启动 / 重载 (/reload) 时执行一次' },
                  { tag: 'minecraft:tick', desc: '每个游戏刻 (20次/秒) 持续循环执行' }
                ].map((preset) => {
                  const isAssigned = tags.includes(preset.tag);
                  return (
                    <button
                      key={preset.tag}
                      type="button"
                      onClick={() => {
                        if (isAssigned) handleRemoveTag(preset.tag);
                        else setTags([...tags, preset.tag]);
                        setIsDirty(true);
                      }}
                      style={{
                        padding: '8px 12px',
                        borderRadius: 'var(--radius-sm)',
                        border: isAssigned
                          ? '1px solid var(--accent-copper)'
                          : '1px solid var(--border-subtle)',
                        background: isAssigned ? 'var(--accent-copper-dim)' : 'var(--bg-surface)',
                        color: isAssigned ? 'var(--accent-copper)' : 'var(--text-main)',
                        textAlign: 'left',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ fontWeight: 600, fontSize: '12px' }}>
                        #{preset.tag}
                      </div>
                      <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                        {preset.desc}
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Custom Tag Input */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-sub)' }}>
                添加自定义函数标签：
              </span>
              <div style={{ display: 'flex', gap: '8px' }}>
                <input
                  type="text"
                  placeholder="例如 copperbench:custom_tick 或 mod:events/on_kill"
                  value={newTag}
                  onChange={(e) => setNewTag(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleAddTag();
                  }}
                  data-testid="function-add-tag-input"
                  style={{ flex: 1 }}
                />
                <button
                  type="button"
                  className="btn-primary"
                  onClick={handleAddTag}
                  disabled={!newTag.trim()}
                  data-testid="function-add-tag-btn"
                >
                  <Plus size={14} />
                  <span>添加标签</span>
                </button>
              </div>
            </div>

            {/* Assigned Tags List */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <span style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-sub)' }}>
                已分配的标签列表：
              </span>
              {tags.length === 0 ? (
                <div
                  style={{
                    padding: '24px',
                    textAlign: 'center',
                    background: 'var(--bg-surface)',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border-subtle)',
                    color: 'var(--text-muted)'
                  }}
                >
                  尚未为此函数分配任何标签。
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {tags.map((tag) => (
                    <div
                      key={tag}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '10px 14px',
                        background: 'var(--bg-surface)',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-sm)'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Tags size={14} color="var(--accent-copper)" />
                        <span
                          style={{
                            fontFamily: 'var(--font-mono)',
                            fontSize: '12px',
                            fontWeight: 600
                          }}
                        >
                          #{tag}
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleRemoveTag(tag)}
                        aria-label={`移除标签 ${tag}`}
                        style={{
                          background: 'none',
                          border: 'none',
                          color: 'var(--badge-red)',
                          cursor: 'pointer',
                          padding: '4px'
                        }}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {activeTab === 'preview' && (
          <div
            style={{
              flex: 1,
              padding: '24px',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: '20px',
              maxWidth: '820px'
            }}
          >
            <div>
              <h2 style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text-main)' }}>
                数据包结构映射预览
              </h2>
              <p style={{ fontSize: '12px', color: 'var(--text-sub)', marginTop: '4px' }}>
                在生成或编译时，此函数将按照 Minecraft Data Pack 规范输出为以下文件：
              </p>
            </div>

            {/* mcfunction file preview */}
            <div
              style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-md)',
                overflow: 'hidden'
              }}
            >
              <div
                style={{
                  padding: '8px 14px',
                  background: 'var(--bg-panel)',
                  borderBottom: '1px solid var(--border-subtle)',
                  fontSize: '11px',
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--accent-copper)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}
              >
                <FileCode2 size={13} />
                <span>
                  data/{namespace}/functions/{element.name}.mcfunction
                </span>
              </div>
              <pre
                style={{
                  padding: '12px 14px',
                  margin: 0,
                  fontSize: '11px',
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--text-main)',
                  whiteSpace: 'pre-wrap',
                  maxHeight: '260px',
                  overflowY: 'auto'
                }}
              >
                {code}
              </pre>
            </div>

            {/* tags json preview */}
            {tags.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-sub)' }}>
                  生成的函数标签 JSON：
                </span>
                {tags.map((tag) => {
                  const parts = tag.split(':');
                  const tagNs = parts.length > 1 ? parts[0] : 'minecraft';
                  const tagName = parts.length > 1 ? parts[1] : parts[0];
                  const jsonPreview = JSON.stringify(
                    {
                      replace: false,
                      values: [`${namespace}:${element.name}`]
                    },
                    null,
                    2
                  );

                  return (
                    <div
                      key={tag}
                      style={{
                        background: 'var(--bg-surface)',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-md)',
                        overflow: 'hidden'
                      }}
                    >
                      <div
                        style={{
                          padding: '8px 14px',
                          background: 'var(--bg-panel)',
                          borderBottom: '1px solid var(--border-subtle)',
                          fontSize: '11px',
                          fontFamily: 'var(--font-mono)',
                          color: 'var(--badge-blue)',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px'
                        }}
                      >
                        <Tags size={13} />
                        <span>
                          data/{tagNs}/tags/functions/{tagName}.json
                        </span>
                      </div>
                      <pre
                        style={{
                          padding: '10px 14px',
                          margin: 0,
                          fontSize: '11px',
                          fontFamily: 'var(--font-mono)',
                          color: 'var(--text-sub)'
                        }}
                      >
                        {jsonPreview}
                      </pre>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
