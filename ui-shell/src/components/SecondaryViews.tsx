import React, { useEffect, useState } from 'react';
import { ExternalLink, Plug, ShieldAlert } from 'lucide-react';
import { legacyPluginBridge } from '../bridge/legacyPluginBridge';
import { AssetBrowserView } from './AssetBrowserView';
import { useWorkbench } from '../context/WorkbenchContext';
import { InstalledPluginInventory, UpstreamToolCatalogProjection } from '../types/contract';

export const AssetsView: React.FC = () => <AssetBrowserView />;

const compatibilityRows = [
  ['A', '资源与生成器', '经兼容测试后在产品工作流中运行'],
  ['B', 'Java 逻辑', '通过 Java 兼容 API 和事件适配运行'],
  ['C', 'Swing 界面', '在独立旧版插件窗口中尽力兼容'],
  ['X', '内部 API 依赖', '拒绝加载或标记为不兼容']
] as const;

export const PluginsView: React.FC = () => {
  const { listInstalledPlugins, getUpstreamTools } = useWorkbench();
  const [opening, setOpening] = useState(false);
  const [openError, setOpenError] = useState<string | null>(null);
  const [inventory, setInventory] = useState<InstalledPluginInventory | null>(null);
  const [inventoryError, setInventoryError] = useState<string | null>(null);
  const [upstreamTools, setUpstreamTools] = useState<UpstreamToolCatalogProjection | null>(null);

  useEffect(() => {
    let cancelled = false;
    void listInstalledPlugins()
      .then((result) => {
        if (!cancelled)
          setInventory(result);
      })
      .catch((error: unknown) => {
        if (!cancelled)
          setInventoryError(error instanceof Error ? error.message : '插件清单无法加载');
      });
    void getUpstreamTools()
      .then((result) => {
        if (!cancelled)
          setUpstreamTools(result);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [listInstalledPlugins, getUpstreamTools]);

  const openLegacyWindow = async () => {
    setOpening(true);
    setOpenError(null);
    try {
      await legacyPluginBridge.open();
    } catch (error) {
      setOpenError(error instanceof Error ? error.message : '旧版插件窗口无法打开');
    } finally {
      setOpening(false);
    }
  };

  return (
    <div className="animate-fade-in" style={{ flex: 1, padding: '32px', display: 'flex', flexDirection: 'column', gap: '20px', overflow: 'auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <Plug size={24} color="var(--accent-copper)" aria-hidden="true" />
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>MCreator 插件兼容中心</h2>
      </div>

      <p style={{ color: 'var(--text-muted)', lineHeight: '1.6', maxWidth: '760px' }}>
        插件按其依赖的扩展点分级。Java 插件默认禁用，启用或版本哈希变化仍需要用户明确确认。清单只做静态扫描，不会加载 Java 代码。
      </p>

      <div data-testid="installed-plugin-inventory">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>已安装插件</h3>
        {inventoryError && (
          <div role="alert" style={{ color: 'var(--badge-red)', fontSize: '12px' }}>{inventoryError}</div>
        )}
        {!inventory && !inventoryError && (
          <div style={{ color: 'var(--text-muted)', fontSize: '12px' }}>正在读取插件清单…</div>
        )}
        {inventory && (
          <table style={{ borderCollapse: 'collapse', width: 'min(960px, 100%)', fontSize: '13px' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-active)', textAlign: 'left' }}>
                <th style={{ padding: '10px 8px' }}>插件</th>
                <th style={{ padding: '10px 8px', width: '72px' }}>等级</th>
                <th style={{ padding: '10px 8px' }}>来源</th>
                <th style={{ padding: '10px 8px' }}>路由</th>
              </tr>
            </thead>
            <tbody>
              {inventory.plugins.map((plugin) => (
                <tr key={plugin.pluginId + plugin.path} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                  <td style={{ padding: '12px 8px' }}>{plugin.displayName ?? plugin.pluginId}</td>
                  <td style={{ padding: '12px 8px', fontWeight: 700 }}>{plugin.level}</td>
                  <td style={{ padding: '12px 8px', color: 'var(--text-muted)' }}>{plugin.firstParty ? '第一方' : '用户/第三方'}</td>
                  <td style={{ padding: '12px 8px', color: 'var(--text-muted)' }}>{plugin.route}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {upstreamTools && (
        <div data-testid="upstream-tool-catalog">
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>上游工具去向</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '12px', lineHeight: '1.5', maxWidth: '960px' }}>
            {upstreamTools.notes}
          </p>
          <table style={{ borderCollapse: 'collapse', width: 'min(960px, 100%)', fontSize: '13px', marginTop: '8px' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-active)', textAlign: 'left' }}>
                <th style={{ padding: '10px 8px' }}>上游入口</th>
                <th style={{ padding: '10px 8px', width: '120px' }}>去向</th>
                <th style={{ padding: '10px 8px' }}>说明</th>
              </tr>
            </thead>
            <tbody>
              {upstreamTools.tools.map((tool) => (
                <tr key={tool.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                  <td style={{ padding: '12px 8px' }}>{tool.upstream}</td>
                  <td style={{ padding: '12px 8px', fontWeight: 700 }}>{tool.surface}</td>
                  <td style={{ padding: '12px 8px', color: 'var(--text-muted)' }}>{tool.notes}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <table style={{ borderCollapse: 'collapse', width: 'min(760px, 100%)', fontSize: '13px' }}>
        <thead>
          <tr style={{ borderBottom: '1px solid var(--border-active)', textAlign: 'left' }}>
            <th style={{ padding: '10px 8px', width: '72px' }}>等级</th>
            <th style={{ padding: '10px 8px', width: '180px' }}>类型</th>
            <th style={{ padding: '10px 8px' }}>处理方式</th>
          </tr>
        </thead>
        <tbody>
          {compatibilityRows.map(([level, type, route]) => (
            <tr key={level} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
              <td style={{ padding: '12px 8px', fontWeight: 700 }}>{level}</td>
              <td style={{ padding: '12px 8px' }}>{type}</td>
              <td style={{ padding: '12px 8px', color: 'var(--text-muted)' }}>{route}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', maxWidth: '760px', color: 'var(--badge-amber)' }}>
        <ShieldAlert size={18} aria-hidden="true" style={{ flex: '0 0 auto', marginTop: '2px' }} />
        <span style={{ color: 'var(--text-muted)', lineHeight: '1.6' }}>
          旧版窗口运行上游 Swing 扩展点，不继承新工作台的视觉与布局保证。关闭窗口不会卸载插件逻辑。
        </span>
      </div>

      <div>
        <button
          type="button"
          className="btn-primary"
          data-testid="open-legacy-plugin-window"
          disabled={!legacyPluginBridge.available || opening}
          title={legacyPluginBridge.available ? '在独立系统窗口中打开' : '桌面宿主中可用'}
          onClick={() => void openLegacyWindow()}
        >
          <ExternalLink size={15} aria-hidden="true" />
          <span>{opening ? '正在打开...' : '打开旧版插件窗口'}</span>
        </button>
        {!legacyPluginBridge.available && (
          <div style={{ marginTop: '8px', color: 'var(--text-muted)', fontSize: '12px' }}>
            当前浏览器预览不连接 Swing 宿主。
          </div>
        )}
        {openError && (
          <div role="alert" style={{ marginTop: '8px', color: 'var(--badge-red)', fontSize: '12px' }}>
            {openError}
          </div>
        )}
      </div>
    </div>
  );
};
