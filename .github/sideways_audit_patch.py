from pathlib import Path

path = Path('app/src/main/java/com/limelight/Game.java')
text = path.read_text(encoding='utf-8')

# Ignore stale pre-portrait layout callbacks. setRequestedOrientation(PORTRAIT) can cause another
# configuration/layout pass while an earlier transform post is still queued.
old = '''    private FrameLayout gamePhysicalRoot;\n    private FrameLayout gameVisualRoot;\n    private boolean sidewaysStreamActive;\n    private ClipboardManager clipboardManager;\n'''
new = '''    private FrameLayout gamePhysicalRoot;\n    private FrameLayout gameVisualRoot;\n    private boolean sidewaysStreamActive;\n    private int sidewaysTransformGeneration;\n    private ClipboardManager clipboardManager;\n'''
if old in text:
    text = text.replace(old, new, 1)

old = '''    private void applySidewaysVisualTransform() {\n        if (gamePhysicalRoot == null || gameVisualRoot == null) return;\n        gamePhysicalRoot.post(() -> {\n            if (isFinishing() || gamePhysicalRoot == null || gameVisualRoot == null) return;\n            int physicalWidth = gamePhysicalRoot.getWidth();\n            int physicalHeight = gamePhysicalRoot.getHeight();\n            if (physicalWidth <= 0 || physicalHeight <= 0) return;\n\n            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) gameVisualRoot.getLayoutParams();\n            params.gravity = Gravity.CENTER;\n            if (sidewaysStreamActive) {\n                params.width = SidewaysStreamPolicy.logicalWidth(\n                        physicalWidth, physicalHeight, true);\n                params.height = SidewaysStreamPolicy.logicalHeight(\n                        physicalWidth, physicalHeight, true);\n            } else {\n                params.width = ViewGroup.LayoutParams.MATCH_PARENT;\n                params.height = ViewGroup.LayoutParams.MATCH_PARENT;\n            }\n            gameVisualRoot.setLayoutParams(params);\n            gameVisualRoot.setRotation(sidewaysStreamActive\n                    ? SidewaysStreamPolicy.rotationDegrees(prefConfig.sidewaysStreamMode)\n                    : 0f);\n            gameVisualRoot.post(() -> {\n                if (gameVisualRoot == null) return;\n                gameVisualRoot.setPivotX(gameVisualRoot.getWidth() / 2f);\n                gameVisualRoot.setPivotY(gameVisualRoot.getHeight() / 2f);\n                refreshSidewaysDependentLayouts();\n            });\n        });\n    }\n'''
new = '''    private void applySidewaysVisualTransform() {\n        if (gamePhysicalRoot == null || gameVisualRoot == null) return;\n        final int generation = ++sidewaysTransformGeneration;\n        gamePhysicalRoot.post(() -> {\n            if (generation != sidewaysTransformGeneration || isFinishing() ||\n                    gamePhysicalRoot == null || gameVisualRoot == null) return;\n            int physicalWidth = gamePhysicalRoot.getWidth();\n            int physicalHeight = gamePhysicalRoot.getHeight();\n            if (physicalWidth <= 0 || physicalHeight <= 0) return;\n\n            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) gameVisualRoot.getLayoutParams();\n            params.gravity = Gravity.CENTER;\n            if (sidewaysStreamActive) {\n                params.width = SidewaysStreamPolicy.logicalWidth(\n                        physicalWidth, physicalHeight, true);\n                params.height = SidewaysStreamPolicy.logicalHeight(\n                        physicalWidth, physicalHeight, true);\n            } else {\n                params.width = ViewGroup.LayoutParams.MATCH_PARENT;\n                params.height = ViewGroup.LayoutParams.MATCH_PARENT;\n            }\n            gameVisualRoot.setLayoutParams(params);\n            gameVisualRoot.setRotation(sidewaysStreamActive\n                    ? SidewaysStreamPolicy.rotationDegrees(prefConfig.sidewaysStreamMode)\n                    : 0f);\n            gameVisualRoot.post(() -> {\n                if (generation != sidewaysTransformGeneration || isFinishing() ||\n                        gameVisualRoot == null) return;\n                gameVisualRoot.setPivotX(gameVisualRoot.getWidth() / 2f);\n                gameVisualRoot.setPivotY(gameVisualRoot.getHeight() / 2f);\n                refreshSidewaysDependentLayouts();\n            });\n        });\n    }\n'''
if old in text:
    text = text.replace(old, new, 1)
if 'final int generation = ++sidewaysTransformGeneration;' not in text:
    raise SystemExit('Sideways transform generation guard did not apply')

# Normal orientation can keep the existing immediate controller rebuild. In sideways mode the
# visual root's swapped dimensions are only authoritative after its post-layout pass, so rebuilding
# here causes a physical-size rebuild followed by a logical-size rebuild (flicker/state churn).
old = '''        if (virtualController != null) {\n            // Refresh layout of OSC for possible new screen size\n            virtualController.refreshLayout();\n        }\n\n        if(keyBoardController != null){\n            keyBoardController.refreshLayout();\n        }\n\n        if(keyBoardLayoutController != null){\n            keyBoardLayoutController.refreshLayout();\n        }\n'''
new = '''        if (!sidewaysStreamActive) {\n            if (virtualController != null) {\n                // Refresh layout of OSC for possible new screen size\n                virtualController.refreshLayout();\n            }\n\n            if (keyBoardController != null) {\n                keyBoardController.refreshLayout();\n            }\n\n            if (keyBoardLayoutController != null) {\n                keyBoardLayoutController.refreshLayout();\n            }\n        }\n'''
if old in text:
    text = text.replace(old, new, 1)
if 'if (!sidewaysStreamActive) {' not in text:
    raise SystemExit('Sideways controller refresh ownership guard did not apply')

path.write_text(text, encoding='utf-8')
print('Applied second-pass sideways lifecycle hardening')
