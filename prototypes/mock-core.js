/**
 * UI-Core v0.1 Mock Adapter & Scenario Runner
 * 完全基于 ui-core/fixtures/v0.1/scenarios 规范，无真实文件系统依赖
 */

const SCENARIOS = {
  "ready": {
    "schemaVersion": "0.1",
    "scenarioId": "ready",
    "extendsScenarioId": null,
    "title": { "key": "scenario.ready", "fallback": "就绪工作区 (Ready Workspace)" },
    "description": "健康正常的 Fabric 1.21.1 工作区，包含近期创建的方块与物品，无活动构建任务。",
    "viewportState": "ready",
    "initialMessages": [
      {
        "messageType": "query_result",
        "schemaVersion": "0.1",
        "requestId": "req-init-workbench",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "get_workbench",
        "status": "succeeded",
        "revision": 42,
        "data": {
          "workspace": {
            "id": "11111111-1111-4111-8111-111111111111",
            "name": "Copper Trails",
            "kind": "mod",
            "revision": 42,
            "dirty": false,
            "generator": {
              "id": "fabric-1.21.1",
              "loader": "fabric",
              "minecraftVersion": "1.21.1",
              "displayName": "Fabric 1.21.1",
              "state": "ready"
            },
            "lock": { "state": "write_available", "holder": null },
            "compatibility": { "mode": "native", "unknownDataPreserved": true }
          },
          "permission": {
            "profile": "workspace",
            "canRequestElevation": true,
            "protectedOperationsAlwaysConfirm": true
          },
          "connection": { "core": "connected", "network": "online", "bridge": "ready" },
          "elementCounts": { "total": 4, "valid": 3, "invalid": 0, "draft": 1, "unsupported": 0 },
          "activeTasks": [],
          "capabilities": [
            { "id": "mod_elements.create", "availability": "available", "reasonCode": null, "message": null, "affectedPaths": [] },
            { "id": "workspace.build", "availability": "available", "reasonCode": null, "message": null, "affectedPaths": [] }
          ],
          "recentElements": [
            {
              "id": "22222222-2222-4222-8222-222222222221",
              "type": "block",
              "name": "copper_lamp",
              "displayName": "Copper Lamp (铜灯)",
              "state": "valid",
              "ownership": "generated",
              "updatedAt": "2026-08-16T06:20:00Z",
              "diagnostics": { "error": 0, "warning": 0, "info": 0 }
            },
            {
              "id": "22222222-2222-4222-8222-222222222222",
              "type": "item",
              "name": "trail_compass",
              "displayName": "Trail Compass (探险指南针)",
              "state": "draft",
              "ownership": "generated",
              "updatedAt": "2026-08-16T06:15:00Z",
              "diagnostics": { "error": 0, "warning": 1, "info": 0 }
            }
          ]
        },
        "diagnostics": []
      },
      {
        "messageType": "query_result",
        "schemaVersion": "0.1",
        "requestId": "req-init-elements",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "list_mod_elements",
        "status": "succeeded",
        "revision": 42,
        "data": {
          "items": [
            {
              "id": "22222222-2222-4222-8222-222222222221",
              "type": "block",
              "name": "copper_lamp",
              "displayName": "Copper Lamp (铜灯)",
              "state": "valid",
              "ownership": "generated",
              "updatedAt": "2026-08-16T06:20:00Z",
              "diagnostics": { "error": 0, "warning": 0, "info": 0 }
            },
            {
              "id": "22222222-2222-4222-8222-222222222222",
              "type": "item",
              "name": "trail_compass",
              "displayName": "Trail Compass (探险指南针)",
              "state": "draft",
              "ownership": "generated",
              "updatedAt": "2026-08-16T06:15:00Z",
              "diagnostics": { "error": 0, "warning": 1, "info": 0 }
            },
            {
              "id": "22222222-2222-4222-8222-222222222224",
              "type": "recipe",
              "name": "copper_lamp_recipe",
              "displayName": "Copper Lamp Recipe (铜灯合成配方)",
              "state": "valid",
              "ownership": "generated",
              "updatedAt": "2026-08-16T06:10:00Z",
              "diagnostics": { "error": 0, "warning": 0, "info": 0 }
            },
            {
              "id": "22222222-2222-4222-8222-222222222225",
              "type": "procedure",
              "name": "toggle_lamp_glow",
              "displayName": "Toggle Lamp Glow (开关发光逻辑)",
              "state": "valid",
              "ownership": "generated",
              "updatedAt": "2026-08-16T06:05:00Z",
              "diagnostics": { "error": 0, "warning": 0, "info": 0 }
            }
          ],
          "page": 1,
          "pageSize": 50,
          "total": 4,
          "availableTypes": ["block", "item", "recipe", "procedure"]
        },
        "diagnostics": []
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": null,
      "primaryAction": "create_mod_element",
      "focusTarget": "[data-testid=workbench-main]"
    }
  },

  "empty-workspace": {
    "schemaVersion": "0.1",
    "scenarioId": "empty-workspace",
    "title": { "key": "scenario.empty_workspace", "fallback": "空工作区 (Empty Workspace)" },
    "description": "新建的 Fabric 工作区，尚无任何模组元素，引导用户创建第一个方块或物品。",
    "viewportState": "empty",
    "initialMessages": [
      {
        "messageType": "query_result",
        "schemaVersion": "0.1",
        "requestId": "req-empty-wb",
        "workspaceId": "11111111-1111-4111-8111-111111111112",
        "operation": "get_workbench",
        "status": "succeeded",
        "revision": 0,
        "data": {
          "workspace": {
            "id": "11111111-1111-4111-8111-111111111112",
            "name": "New Fabric Mod",
            "kind": "mod",
            "revision": 0,
            "dirty": false,
            "generator": {
              "id": "fabric-1.21.1",
              "loader": "fabric",
              "minecraftVersion": "1.21.1",
              "displayName": "Fabric 1.21.1",
              "state": "ready"
            },
            "lock": { "state": "write_available", "holder": null },
            "compatibility": { "mode": "native", "unknownDataPreserved": true }
          },
          "permission": { "profile": "workspace", "canRequestElevation": true, "protectedOperationsAlwaysConfirm": true },
          "connection": { "core": "connected", "network": "online", "bridge": "ready" },
          "elementCounts": { "total": 0, "valid": 0, "invalid": 0, "draft": 0, "unsupported": 0 },
          "activeTasks": [],
          "capabilities": [
            { "id": "mod_elements.create", "availability": "available", "reasonCode": null, "message": null, "affectedPaths": [] }
          ],
          "recentElements": []
        },
        "diagnostics": []
      },
      {
        "messageType": "query_result",
        "schemaVersion": "0.1",
        "requestId": "req-empty-el",
        "workspaceId": "11111111-1111-4111-8111-111111111112",
        "operation": "list_mod_elements",
        "status": "succeeded",
        "revision": 0,
        "data": {
          "items": [],
          "page": 1,
          "pageSize": 50,
          "total": 0,
          "availableTypes": ["block", "item", "recipe", "procedure"]
        },
        "diagnostics": []
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": "workspace_empty",
      "primaryAction": "create_mod_element",
      "focusTarget": "[data-testid=empty-primary-action]"
    }
  },

  "build-running": {
    "schemaVersion": "0.1",
    "scenarioId": "build-running",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.build_running", "fallback": "构建流水线运行中 (Build in Progress)" },
    "description": "模组构建任务已受理，模拟 3 阶段进度推进、流式编译日志追加与最终完成。",
    "viewportState": "ready",
    "initialMessages": [
      {
        "messageType": "command_result",
        "schemaVersion": "0.1",
        "requestId": "req-build-start",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "build_workspace",
        "status": "accepted",
        "newRevision": 42,
        "recoveryPointId": null,
        "task": {
          "id": "33333333-3333-4333-8333-333333333331",
          "kind": "build",
          "state": "running",
          "cancellable": true,
          "progress": 0.1,
          "stage": { "key": "task.resolving_dependencies", "fallback": "正在解析 Gradle 依赖 (Resolving dependencies)" },
          "startedAt": new Date(Date.now() - 2000).toISOString(),
          "completedAt": null,
          "diagnostics": { "error": 0, "warning": 0, "info": 0 }
        },
        "data": null,
        "diagnostics": [],
        "conflict": null,
        "denial": null
      }
    ],
    "timeline": [
      {
        "afterMs": 800,
        "message": {
          "messageType": "event",
          "schemaVersion": "0.1",
          "eventId": "evt-build-prog-1",
          "workspaceId": "11111111-1111-4111-8111-111111111111",
          "revision": 42,
          "sequence": 103,
          "occurredAt": new Date().toISOString(),
          "event": "task_progressed",
          "payload": {
            "task": {
              "id": "33333333-3333-4333-8333-333333333331",
              "kind": "build",
              "state": "running",
              "cancellable": true,
              "progress": 0.65,
              "stage": { "key": "task.compiling", "fallback": "正在编译生成的 Java 源码 (Compiling generated sources)" },
              "startedAt": new Date(Date.now() - 2000).toISOString(),
              "completedAt": null,
              "diagnostics": { "error": 0, "warning": 0, "info": 0 }
            }
          }
        }
      },
      {
        "afterMs": 1400,
        "message": {
          "messageType": "event",
          "schemaVersion": "0.1",
          "eventId": "evt-build-log-1",
          "workspaceId": "11111111-1111-4111-8111-111111111111",
          "revision": 42,
          "sequence": 104,
          "occurredAt": new Date().toISOString(),
          "event": "task_log_appended",
          "payload": {
            "taskId": "33333333-3333-4333-8333-333333333331",
            "entries": [
              { "sequence": 1, "timestamp": new Date().toISOString(), "level": "info", "text": "[Gradle] :compileJava UP-TO-DATE" },
              { "sequence": 2, "timestamp": new Date().toISOString(), "level": "info", "text": "[Fabric] Generated 4 mod elements successfully into .fabric/sources." },
              { "sequence": 3, "timestamp": new Date().toISOString(), "level": "info", "text": "[Jar] Building mod artifact: CopperTrails-1.21.1-0.1.0.jar" }
            ]
          }
        }
      },
      {
        "afterMs": 2200,
        "message": {
          "messageType": "event",
          "schemaVersion": "0.1",
          "eventId": "evt-build-done",
          "workspaceId": "11111111-1111-4111-8111-111111111111",
          "revision": 42,
          "sequence": 105,
          "occurredAt": new Date().toISOString(),
          "event": "task_completed",
          "payload": {
            "task": {
              "id": "33333333-3333-4333-8333-333333333331",
              "kind": "build",
              "state": "succeeded",
              "cancellable": false,
              "progress": 1.0,
              "stage": { "key": "task.completed", "fallback": "构建完成！(Build Completed)" },
              "startedAt": new Date(Date.now() - 2200).toISOString(),
              "completedAt": new Date().toISOString(),
              "diagnostics": { "error": 0, "warning": 0, "info": 0 }
            }
          }
        }
      }
    ],
    "expectedUi": {
      "announcement": "build_started",
      "primaryAction": "cancel_task",
      "focusTarget": "[data-task-id=33333333-3333-4333-8333-333333333331]"
    }
  },

  "validation-failed": {
    "schemaVersion": "0.1",
    "scenarioId": "validation-failed",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.validation_failed", "fallback": "字段级校验失败 (Validation Failed)" },
    "description": "元素更新因数值超出有效范围被拒绝，Core 返回字段级诊断与修复指引。",
    "viewportState": "error",
    "initialMessages": [
      {
        "messageType": "command_result",
        "schemaVersion": "0.1",
        "requestId": "req-val-fail",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "update_mod_element",
        "status": "rejected",
        "newRevision": 42,
        "recoveryPointId": null,
        "task": null,
        "data": null,
        "diagnostics": [
          {
            "code": "FIELD_VALUE_OUT_OF_RANGE",
            "severity": "error",
            "message": {
              "key": "diagnostic.field_value_out_of_range",
              "fallback": "方块硬度 (Hardness) 必须介于 0 到 100 之间。",
              "args": { "min": 0, "max": 100 }
            },
            "path": "/elements/22222222-2222-4222-8222-222222222221/fields/hardness",
            "elementId": "22222222-2222-4222-8222-222222222221",
            "recoverable": true,
            "actions": [
              {
                "id": "open_invalid_field",
                "label": { "key": "action.open_field", "fallback": "定位并修改硬度字段" },
                "kind": "open_field",
                "target": "/fields/hardness"
              }
            ]
          }
        ],
        "conflict": null,
        "denial": null
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": "FIELD_VALUE_OUT_OF_RANGE",
      "primaryAction": "open_invalid_field",
      "focusTarget": "[data-field-path=/fields/hardness]"
    }
  },

  "permission-denied": {
    "schemaVersion": "0.1",
    "scenarioId": "permission-denied",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.permission_denied", "fallback": "权限不足拦截 (Permission Denied)" },
    "description": "当前处于只读 (Read Only) 模式，尝试触发模组构建被安全拦截，提示申请工作区权限。",
    "viewportState": "error",
    "initialMessages": [
      {
        "messageType": "command_result",
        "schemaVersion": "0.1",
        "requestId": "req-perm-denied",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "build_workspace",
        "status": "rejected",
        "newRevision": 42,
        "recoveryPointId": null,
        "task": null,
        "data": null,
        "diagnostics": [
          {
            "code": "PERMISSION_PROFILE_DENIED",
            "severity": "error",
            "message": {
              "key": "diagnostic.permission_profile_denied",
              "fallback": "当前会话为只读权限 (Read Only)，执行构建需要工作区 (Workspace) 权限档位。"
            },
            "path": null,
            "elementId": null,
            "recoverable": true,
            "actions": [
              {
                "id": "request_workspace_permission",
                "label": { "key": "action.request_permission", "fallback": "申请提升至 Workspace 权限" },
                "kind": "request_permission",
                "target": "workspace"
              }
            ]
          }
        ],
        "conflict": null,
        "denial": {
          "currentProfile": "read_only",
          "requiredProfile": "workspace",
          "approvalRequired": false,
          "protectedOperation": false
        }
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": "PERMISSION_PROFILE_DENIED",
      "primaryAction": "request_workspace_permission",
      "focusTarget": "[data-testid=permission-alert]"
    }
  },

  "revision-conflict": {
    "schemaVersion": "0.1",
    "scenarioId": "revision-conflict",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.revision_conflict", "fallback": "多写入者版本冲突 (Revision Conflict)" },
    "description": "外部 MCP 会话已将工作区推进至 Revision 43，当前基于 Revision 42 的保存被拒绝，提供冲突仲裁。",
    "viewportState": "error",
    "initialMessages": [
      {
        "messageType": "command_result",
        "schemaVersion": "0.1",
        "requestId": "req-rev-conflict",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "update_mod_element",
        "status": "rejected",
        "newRevision": 43,
        "recoveryPointId": null,
        "task": null,
        "data": null,
        "diagnostics": [
          {
            "code": "WORKSPACE_REVISION_CONFLICT",
            "severity": "error",
            "message": {
              "key": "diagnostic.workspace_revision_conflict",
              "fallback": "工作区已被外部修改（预期 Revision 42，实际最新为 43）。",
              "args": { "expected": 42, "actual": 43 }
            },
            "path": "/elements/22222222-2222-4222-8222-222222222221",
            "elementId": "22222222-2222-4222-8222-222222222221",
            "recoverable": true,
            "actions": [
              {
                "id": "refresh_element",
                "label": { "key": "action.refresh", "fallback": "查看并载入最新修改 (Review latest changes)" },
                "kind": "refresh",
                "target": "/elements/22222222-2222-4222-8222-222222222221"
              }
            ]
          }
        ],
        "conflict": {
          "expectedRevision": 42,
          "actualRevision": 43,
          "changedPaths": ["/elements/22222222-2222-4222-8222-222222222221/fields/hardness"]
        },
        "denial": null
      }
    ],
    "timeline": [
      {
        "afterMs": 0,
        "message": {
          "messageType": "event",
          "schemaVersion": "0.1",
          "eventId": "evt-rev-adv",
          "workspaceId": "11111111-1111-4111-8111-111111111111",
          "revision": 43,
          "sequence": 101,
          "occurredAt": new Date().toISOString(),
          "event": "workspace_revision_advanced",
          "payload": {
            "changedPaths": ["/elements/22222222-2222-4222-8222-222222222221/fields/hardness"],
            "actor": "mcp"
          }
        }
      }
    ],
    "expectedUi": {
      "announcement": "WORKSPACE_REVISION_CONFLICT",
      "primaryAction": "refresh_element",
      "focusTarget": "[data-testid=revision-conflict-dialog]"
    }
  },

  "partial-capability": {
    "schemaVersion": "0.1",
    "scenarioId": "partial-capability",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.partial_capability", "fallback": "加载器专属字段保留 (Partial Capability)" },
    "description": "Fabric 处于活动状态，NeoForge 专属字段（如火焰蔓延速率）保留原值但标记为只读不可编辑。",
    "viewportState": "degraded",
    "initialMessages": [
      {
        "messageType": "query_result",
        "schemaVersion": "0.1",
        "requestId": "req-partial-editor",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "get_mod_element_editor",
        "status": "succeeded",
        "revision": 42,
        "data": {
          "element": {
            "id": "22222222-2222-4222-8222-222222222221",
            "type": "block",
            "name": "copper_lamp",
            "displayName": "Copper Lamp (铜灯)",
            "state": "valid",
            "ownership": "generated",
            "updatedAt": "2026-08-16T06:20:00Z",
            "diagnostics": { "error": 0, "warning": 1, "info": 0 }
          },
          "sections": [
            {
              "id": "block.behavior",
              "title": { "key": "editor.block_behavior", "fallback": "方块行为属性 (Block Behavior)" },
              "fields": [
                {
                  "path": "/fields/hardness",
                  "label": { "key": "field.hardness", "fallback": "硬度 (Hardness)" },
                  "help": null,
                  "control": "number",
                  "required": true,
                  "readOnly": false,
                  "value": 3.5,
                  "options": [],
                  "constraints": { "min": 0, "max": 100, "step": 0.5 },
                  "diagnostics": []
                },
                {
                  "path": "/loaderExtensions/neoforge/fireSpreadSpeed",
                  "label": { "key": "field.fire_spread_speed", "fallback": "火焰蔓延速率 (NeoForge 专属)" },
                  "help": {
                    "key": "field.loader_specific_preserved",
                    "fallback": "此值为 NeoForge 专属字段，在 Fabric 生成器下保留原值但不可编辑。"
                  },
                  "control": "number",
                  "required": false,
                  "readOnly": true,
                  "value": 5,
                  "options": [],
                  "constraints": { "min": 0, "max": 100, "step": 1 },
                  "diagnostics": []
                }
              ]
            }
          ],
          "capabilities": [
            {
              "id": "block.fire_spread_speed",
              "availability": "unavailable",
              "reasonCode": "ACTIVE_LOADER_UNSUPPORTED_FIELD",
              "message": {
                "key": "capability.active_loader_unsupported_field",
                "fallback": "当前活动加载器为 Fabric，NeoForge 专属字段已保留为只读。",
                "args": { "loader": "fabric" }
              },
              "affectedPaths": ["/loaderExtensions/neoforge/fireSpreadSpeed"]
            }
          ]
        },
        "diagnostics": []
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": "ACTIVE_LOADER_UNSUPPORTED_FIELD",
      "primaryAction": null,
      "focusTarget": "[data-field-path=/loaderExtensions/neoforge/fireSpreadSpeed]"
    }
  },

  "offline": {
    "schemaVersion": "0.1",
    "scenarioId": "offline",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.offline", "fallback": "本地离线模式 (Offline Workspace)" },
    "description": "外部互联网断开，本地 Java Core、模组编辑、离线依赖构建与 Blockbench 正常工作。",
    "viewportState": "degraded",
    "initialMessages": [
      {
        "messageType": "event",
        "schemaVersion": "0.1",
        "eventId": "evt-offline-conn",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "revision": 42,
        "sequence": 102,
        "occurredAt": new Date().toISOString(),
        "event": "connectivity_changed",
        "payload": { "core": "connected", "network": "offline", "bridge": "ready" }
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": "network_offline",
      "primaryAction": null,
      "focusTarget": "[data-testid=offline-status]"
    }
  },

  "external-process-exited": {
    "schemaVersion": "0.1",
    "scenarioId": "external-process-exited",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.external_process_exited", "fallback": "外部测试客户端异常退出 (Crash Exit)" },
    "description": "托管的 Minecraft 测试客户端异常退出（Exit Code: 1），提供错误诊断与一键打开日志。",
    "viewportState": "error",
    "initialMessages": [
      {
        "messageType": "event",
        "schemaVersion": "0.1",
        "eventId": "evt-crash-1",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "revision": 42,
        "sequence": 106,
        "occurredAt": new Date().toISOString(),
        "event": "task_completed",
        "payload": {
          "task": {
            "id": "33333333-3333-4333-8333-333333333332",
            "kind": "run_client",
            "state": "failed",
            "cancellable": false,
            "progress": null,
            "stage": { "key": "task.client_exited", "fallback": "Minecraft 测试客户端异常崩溃 (Exit Code 1)" },
            "startedAt": new Date(Date.now() - 60000).toISOString(),
            "completedAt": new Date().toISOString(),
            "diagnostics": { "error": 1, "warning": 0, "info": 0 }
          }
        }
      },
      {
        "messageType": "event",
        "schemaVersion": "0.1",
        "eventId": "evt-crash-diag",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "revision": 42,
        "sequence": 107,
        "occurredAt": new Date().toISOString(),
        "event": "diagnostics_changed",
        "payload": {
          "counts": { "error": 1, "warning": 0, "info": 0 },
          "diagnostics": [
            {
              "code": "EXTERNAL_PROCESS_EXITED",
              "severity": "error",
              "message": {
                "key": "diagnostic.external_process_exited",
                "fallback": "Minecraft 客户端遇到未捕获异常退出 (Code 1)。建议查看 crash-reports 日志。",
                "args": { "exitCode": 1 }
              },
              "path": null,
              "elementId": null,
              "recoverable": true,
              "actions": [
                {
                  "id": "open_client_logs",
                  "label": { "key": "action.open_logs", "fallback": "查看崩溃日志 (Open Crash Logs)" },
                  "kind": "open_logs",
                  "target": "33333333-3333-4333-8333-333333333332"
                }
              ]
            }
          ]
        }
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": "EXTERNAL_PROCESS_EXITED",
      "primaryAction": "open_client_logs",
      "focusTarget": "[data-testid=task-failure]"
    }
  },

  "bridge-recovery": {
    "schemaVersion": "0.1",
    "scenarioId": "bridge-recovery",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.bridge_recovery", "fallback": "JCEF 渲染崩溃恢复 (Bridge Recovery)" },
    "description": "JCEF 渲染进程异常重启，界面基于最后已提交的 Revision 42 恢复，丢弃未确认请求。",
    "viewportState": "recovery",
    "initialMessages": [
      {
        "messageType": "event",
        "schemaVersion": "0.1",
        "eventId": "evt-bridge-rec",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "revision": 42,
        "sequence": 108,
        "occurredAt": new Date().toISOString(),
        "event": "bridge_recovery_required",
        "payload": {
          "reasonCode": "JCEF_RENDERER_CRASHED",
          "lastCommittedRevision": 42,
          "uncommittedRequestIds": ["req-temp-uncommitted-01"]
        }
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": "JCEF_RENDERER_CRASHED",
      "primaryAction": "refresh",
      "focusTarget": "[data-testid=recovery-view]"
    }
  },

  "loading-workbench": {
    "schemaVersion": "0.1",
    "scenarioId": "loading-workbench",
    "title": { "key": "scenario.loading_workbench", "fallback": "工作区加载中 (Loading Workbench)" },
    "description": "查询请求正在飞行，骨架屏保持结构稳定，绝不显示虚假成功状态。",
    "viewportState": "loading",
    "initialMessages": [
      {
        "messageType": "query",
        "schemaVersion": "0.1",
        "requestId": "req-loading-wb",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "get_workbench",
        "payload": {}
      }
    ],
    "timeline": [],
    "expectedUi": {
      "announcement": "workspace_loading",
      "primaryAction": null,
      "focusTarget": null
    }
  },

  "element-created": {
    "schemaVersion": "0.1",
    "scenarioId": "element-created",
    "extendsScenarioId": "ready",
    "title": { "key": "scenario.element_created", "fallback": "新元素已创建 (Element Created)" },
    "description": "新方块 Signal Lantern 提交成功，Revision 推进到 43，收到不可变事实事件。",
    "viewportState": "ready",
    "initialMessages": [
      {
        "messageType": "command_result",
        "schemaVersion": "0.1",
        "requestId": "req-elem-created",
        "workspaceId": "11111111-1111-4111-8111-111111111111",
        "operation": "create_mod_element",
        "status": "committed",
        "newRevision": 43,
        "recoveryPointId": "rec-snap-43",
        "task": null,
        "data": {
          "element": {
            "id": "22222222-2222-4222-8222-222222222223",
            "type": "block",
            "name": "signal_lantern",
            "displayName": "Signal Lantern (信号灯笼)",
            "state": "draft",
            "ownership": "generated",
            "updatedAt": new Date().toISOString(),
            "diagnostics": { "error": 0, "warning": 0, "info": 0 }
          }
        },
        "diagnostics": [],
        "conflict": null,
        "denial": null
      }
    ],
    "timeline": [
      {
        "afterMs": 0,
        "message": {
          "messageType": "event",
          "schemaVersion": "0.1",
          "eventId": "evt-elem-created",
          "workspaceId": "11111111-1111-4111-8111-111111111111",
          "revision": 43,
          "sequence": 109,
          "occurredAt": new Date().toISOString(),
          "event": "mod_element_created",
          "payload": {
            "element": {
              "id": "22222222-2222-4222-8222-222222222223",
              "type": "block",
              "name": "signal_lantern",
              "displayName": "Signal Lantern (信号灯笼)",
              "state": "draft",
              "ownership": "generated",
              "updatedAt": new Date().toISOString(),
              "diagnostics": { "error": 0, "warning": 0, "info": 0 }
            }
          }
        }
      }
    ],
    "expectedUi": {
      "announcement": "element_created",
      "primaryAction": "open_element_editor",
      "focusTarget": "[data-element-id=22222222-2222-4222-8222-222222222223]"
    }
  }
};

/**
 * Mock UI-Core Engine
 */
class MockCoreEngine {
  constructor() {
    this.currentScenarioId = "ready";
    this.state = this.getInitialState();
    this.listeners = new Set();
    this.activeTimers = [];
  }

  getInitialState() {
    return {
      scenarioId: "ready",
      viewportState: "ready",
      workspace: null,
      permission: { profile: "workspace", canRequestElevation: true, protectedOperationsAlwaysConfirm: true },
      connection: { core: "connected", network: "online", bridge: "ready" },
      elementCounts: { total: 0, valid: 0, invalid: 0, draft: 0, unsupported: 0 },
      elements: [],
      activeTasks: [],
      recentElements: [],
      diagnostics: [],
      logs: [],
      currentEditor: null,
      conflict: null,
      denial: null,
      recovery: null,
      announcement: null
    };
  }

  subscribe(listener) {
    this.listeners.add(listener);
    listener(this.state);
    return () => this.listeners.delete(listener);
  }

  notify() {
    for (const l of this.listeners) {
      l(this.state);
    }
  }

  clearTimers() {
    for (const t of this.activeTimers) {
      clearTimeout(t);
    }
    this.activeTimers = [];
  }

  loadScenario(scenarioId) {
    this.clearTimers();
    const scenario = SCENARIOS[scenarioId];
    if (!scenario) return;

    this.currentScenarioId = scenarioId;
    this.state = this.getInitialState();
    this.state.scenarioId = scenarioId;
    this.state.viewportState = scenario.viewportState || "ready";

    // If extendsScenarioId exists, apply base scenario first
    if (scenario.extendsScenarioId && SCENARIOS[scenario.extendsScenarioId]) {
      const base = SCENARIOS[scenario.extendsScenarioId];
      for (const msg of base.initialMessages) {
        this.processMessage(msg);
      }
    }

    // Apply target initial messages
    for (const msg of scenario.initialMessages) {
      this.processMessage(msg);
    }

    if (scenario.expectedUi) {
      this.state.announcement = scenario.expectedUi.announcement;
    }

    // Timeline playback
    if (scenario.timeline && scenario.timeline.length > 0) {
      for (const step of scenario.timeline) {
        const timer = setTimeout(() => {
          this.processMessage(step.message);
          this.notify();
        }, step.afterMs);
        this.activeTimers.push(timer);
      }
    }

    this.notify();
  }

  processMessage(msg) {
    if (!msg) return;

    if (msg.messageType === "query_result") {
      if (msg.operation === "get_workbench" && msg.data) {
        this.state.workspace = msg.data.workspace;
        this.state.permission = msg.data.permission || this.state.permission;
        this.state.connection = msg.data.connection || this.state.connection;
        this.state.elementCounts = msg.data.elementCounts || this.state.elementCounts;
        this.state.recentElements = msg.data.recentElements || [];
        this.state.activeTasks = msg.data.activeTasks || [];
      } else if (msg.operation === "list_mod_elements" && msg.data) {
        this.state.elements = msg.data.items || [];
      } else if (msg.operation === "get_mod_element_editor" && msg.data) {
        this.state.currentEditor = msg.data;
      }
      if (msg.diagnostics) {
        this.state.diagnostics = msg.diagnostics;
      }
    } else if (msg.messageType === "command_result") {
      if (msg.status === "rejected") {
        if (msg.diagnostics) this.state.diagnostics = msg.diagnostics;
        if (msg.conflict) this.state.conflict = msg.conflict;
        if (msg.denial) this.state.denial = msg.denial;
      } else if (msg.status === "accepted") {
        if (msg.task) {
          this.state.activeTasks = [msg.task];
        }
      } else if (msg.status === "committed") {
        if (msg.data && msg.data.element) {
          const exists = this.state.elements.find(e => e.id === msg.data.element.id);
          if (!exists) {
            this.state.elements.unshift(msg.data.element);
            this.state.elementCounts.total++;
            this.state.elementCounts.draft++;
          }
        }
        if (msg.newRevision && this.state.workspace) {
          this.state.workspace.revision = msg.newRevision;
        }
      }
    } else if (msg.messageType === "event") {
      if (msg.event === "task_progressed" && msg.payload && msg.payload.task) {
        const t = msg.payload.task;
        this.state.activeTasks = [t];
      } else if (msg.event === "task_log_appended" && msg.payload && msg.payload.entries) {
        this.state.logs = [...this.state.logs, ...msg.payload.entries];
      } else if (msg.event === "task_completed" && msg.payload && msg.payload.task) {
        this.state.activeTasks = [msg.payload.task];
      } else if (msg.event === "connectivity_changed" && msg.payload) {
        this.state.connection = msg.payload;
      } else if (msg.event === "diagnostics_changed" && msg.payload) {
        this.state.diagnostics = msg.payload.diagnostics || [];
      } else if (msg.event === "workspace_revision_advanced" && msg.payload) {
        if (this.state.workspace) {
          this.state.workspace.revision = msg.revision;
        }
      } else if (msg.event === "bridge_recovery_required" && msg.payload) {
        this.state.recovery = msg.payload;
      } else if (msg.event === "mod_element_created" && msg.payload && msg.payload.element) {
        const el = msg.payload.element;
        const exists = this.state.elements.find(e => e.id === el.id);
        if (!exists) {
          this.state.elements.unshift(el);
        }
      }
    }
  }

  // Interactive UI Commands Simulation
  triggerBuild() {
    this.loadScenario("build-running");
  }

  triggerCreateElement(type = "block", name = "signal_lantern", displayName = "Signal Lantern (信号灯笼)") {
    this.loadScenario("element-created");
  }

  requestWorkspacePermission() {
    this.state.permission.profile = "workspace";
    this.state.denial = null;
    this.state.diagnostics = [];
    this.notify();
  }

  resolveConflict() {
    this.state.conflict = null;
    this.state.diagnostics = [];
    if (this.state.workspace) this.state.workspace.revision = 43;
    this.notify();
  }

  recoverBridge() {
    this.loadScenario("ready");
  }
}

// Global singleton instance for prototype pages
window.mockCore = new MockCoreEngine();
window.SCENARIOS = SCENARIOS;
