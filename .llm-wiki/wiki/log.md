# Wiki Update Log

## 2026-11-11

- **ensure\_page**: {"page_type":"concept","path":"concepts/litert-lm-gpu-backend-and-engine-init","title":"LiteRT-LM GPU backend and engine init"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/localglasses-stats-sections","title":"LocalGlasses stats sections"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/localglasses-dictation","title":"LocalGlasses dictation"}
- **retro**: {"category":"bugfix","slug":"litert-gpu-silent-cpu-fallback-missing-cache-dir","title":"GPU slower: silent CPU fallback from missing cache dir"}
- **observe**: {"relevance":"medium","slug":"obs-2026-11-11-conditional-silence-endpoint-contradicted-the-spec","title":"Conditional silence endpoint contradicted the spec"}
- **retro**: {"category":"debugging","slug":"huggingface-401-means-wrong-repo-not-blocked-network","title":"HF 401 means wrong repo, not a blocked network"}
- **observe**: {"relevance":"medium","slug":"obs-2026-11-11-openspec-change-speech-to-text-planned","title":"OpenSpec change speech-to-text planned"}
- **retro**: {"category":"api","slug":"localglasses-dat-audio-paths","title":"DAT glasses audio: Speech vs camera PCM"}
- **retro**: {"category":"architecture","slug":"localglasses-stt-sherpa-onnx-whisper-small","title":"LocalGlasses STT: sherpa-onnx + Whisper small.en int8"}

## 2026-11-10

- **ensure\_page**: {"page_type":"concept","path":"concepts/android-agent-anr-main-thread-litert-inference","title":"Android agent ANR: main-thread LiteRT inference"}
- **retro**: {"category":"android","slug":"litert-conversation-benchmarkinfo-null","title":"LiteRT conversation BenchmarkInfo can be null"}
- **retro**: {"category":"android","slug":"android-proc-stat-selinux-inference-metrics","title":"Android app cannot read /proc/stat on CPH2717"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-10-openspec-change-stats-for-nerds-created","title":"OpenSpec change stats-for-nerds created"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-10-localglasses-stats-for-nerds-design-settled","title":"LocalGlasses Stats for nerds design settled"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-10-localglasses-agent-vertical-slice-working","title":"LocalGlasses agent vertical slice working"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-10-openspec-change-created-for-localglasses-agent","title":"OpenSpec change created for LocalGlasses agent"}
- **decision**: {"date":"2026-11-11","key_corrections":["device is MediaTek MT6897 with 7.65 GB RAM, not Snapdragon 8 GB","Android has no MCP stdio transport; LiteRT-LM @Tool + constrained decoding replaces localhost MCP","every action requires an editable plan and approval, superseding risky-only auto-run"],"pages":["syntheses/localglasses-on-device-agent-architecture","concepts/gemma-4-e2b-on-litert-lm-for-android","concepts/accessibility-driven-android-agent-safety-model"],"result":"Grill Me complete; 44 turns recorded; GRILL-ME.md saved","topic":"LocalGlasses on-device agent"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-10-localglasses-agent-scope-and-safety-model-decided","title":"LocalGlasses agent scope and safety model decided"}
- **retro**: {"category":"security","slug":"accessibility-agent-hard-blocks-before-trace","title":"Hard blocks before trace capture"}
- **retro**: {"category":"architecture","slug":"litert-lm-tool-calling-replaces-mcp-on-android","title":"LiteRT-LM tool calling replaces MCP on Android"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/accessibility-driven-android-agent-safety-model","title":"Accessibility-driven Android agent safety model"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/gemma-4-e2b-on-litert-lm-for-android","title":"Gemma 4 E2B on LiteRT-LM for Android"}
- **ensure\_page**: {"page_type":"synthesis","path":"syntheses/localglasses-on-device-agent-architecture","title":"LocalGlasses on-device agent architecture"}

## 2026-11-07

- **observe**: {"relevance":"medium","slug":"obs-2026-11-07-asd-ste100-skill-installed-to-global-pi-skills","title":"ASD-STE100 skill installed to global Pi skills"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-07-workflow-set-to-grill-me-openspec-cosmetic-extensions-remove","title":"Workflow set to Grill Me + OpenSpec; cosmetic extensions removed"}
- **retro**: {"category":"security","slug":"key-leak-via-committed-web-captures","title":"API key leak via committed wiki web captures"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-07-localglasses-pushed-to-github-and-verified-via-clean-clone","title":"LocalGlasses pushed to GitHub and verified via clean clone"}
- **milestone**: {"fixes":["BLUETOOTH_CONNECT before Wearables.initialize","string-typed DAT manifest meta-data","RequestPermissionContract for DAT CAMERA","planar I420 VideoFrame layout"],"open":["video recording not yet confirmed on real glasses after I420 fix"],"project":"LocalGlasses","removed":["mwdat-mockdevice","mock/MockDevice.kt","mock UI","host CAMERA permission"],"summary":"Real Meta glasses feed working end-to-end on OnePlus CPH2717 + RB Meta 0HD0","verified":["register REGISTERED","session STARTED","stream STREAMING 504x896","photo 1080x1440 saved"]}
- **retro**: {"category":"android-media","slug":"yuv-h264-mediacodec-mediatek-traps","title":"YUV to H.264 on MediaTek: black frames, JNI segfault, thread safety"}
- **retro**: {"category":"dat","slug":"dat-bluetooth-and-manifest-typed-metadata","title":"DAT silent registration killers: BT permission order and typed meta-data"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-07-localglasses-now-real-glasses-only-mock-and-phone-camera-rem","title":"LocalGlasses now real-glasses-only; mock and phone camera removed"}
- **observe**: {"relevance":"high","slug":"obs-2026-11-07-dat-no-video-feed-had-two-causes-no-preview-code-no-mock-cam","title":"DAT \"no video feed\" had two causes: no preview code + no mock camera source"}
- **retro**: {"category":"architecture","slug":"project-vault-plus-personal-pointer","title":"Project vault with a personal pointer page"}
- **ensure\_page**: {"page_type":"synthesis","path":"syntheses/localglasses-android-setup","title":"LocalGlasses Android setup"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/gradle-and-jdk-bootstrap-without-a-system-toolchain","title":"Gradle and JDK bootstrap without a system toolchain"}
- **ensure\_page**: {"page_type":"entity","path":"entities/pi-coding-agent","title":"Pi coding agent"}
- **ensure\_page**: {"page_type":"entity","path":"entities/meta-ai-app","title":"Meta AI app"}
- **ensure\_page**: {"page_type":"entity","path":"entities/localglasses-app","title":"LocalGlasses app"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/dat-ai-assisted-setup","title":"DAT AI-assisted setup"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/dat-developer-mode","title":"DAT Developer Mode"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/dat-session-lifecycle","title":"DAT session lifecycle"}
- **ensure\_page**: {"page_type":"concept","path":"concepts/agp-9-built-in-kotlin","title":"AGP 9 built-in Kotlin"}
- **ensure\_page**: {"page_type":"entity","path":"entities/android-studio-and-bundled-jbr","title":"Android Studio (and bundled JBR)"}
- **ensure\_page**: {"page_type":"entity","path":"entities/android-gradle-plugin","title":"Android Gradle Plugin"}
- **ensure\_page**: {"page_type":"entity","path":"entities/mock-device-kit","title":"Mock Device Kit"}
- **ensure\_page**: {"page_type":"entity","path":"entities/wearables-mcp-server","title":"Wearables MCP server"}
- **ensure\_page**: {"page_type":"entity","path":"entities/meta-wearables-device-access-toolkit-android-sdk","title":"Meta Wearables Device Access Toolkit (Android SDK)"}
- **capture**: {"format":"web","source_id":"SRC-2026-11-07-002","url":"https://wearables.developer.meta.com/docs/develop/dat/ai-assisted-mcp/"}
- **capture**: {"format":"web","source_id":"SRC-2026-11-07-001","url":"https://wearables.developer.meta.com/docs/develop/dat/getting-started-toolkit/"}
- **capture**: {"format":"web","source_id":"SRC-2026-11-07-003","url":"https://developer.android.com/build/migrate-to-built-in-kotlin"}
- **bootstrap**: {"mode":"company","topic":"LocalGlasses — Meta Wearables Device Access Toolkit Android app"}
