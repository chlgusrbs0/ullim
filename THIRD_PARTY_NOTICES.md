# Third-party notices and provenance

이 고지는 울림이 배포하는 외부 모델·라이브러리와 참고 자료를 구분합니다. **울림 자체 코드의 프로젝트 라이선스를 부여하거나 변경하지 않습니다.** 프로젝트 전체 LICENSE는 추가하지 않았습니다.

## 직접 포함한 모델·자산

| 구성요소 | 원저자·버전 | 출처와 배포 고지 |
|---|---|---|
| YAMNet classification TFLite | Google / TensorFlow authors, `google/lite-model/yamnet/classification/tflite/1`, version1 | [공식 배포 파일](https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite), [공식 사용 예](https://developers.google.com/codelabs/tflite-audio-classification-basic-android). 모델 metadata의 Apache-2.0, [원문 고지](app/src/main/assets/notices/Apache-2.0.txt) 보존 |
| 521개 레이블 맵 | 같은 모델 ZIP entry `yamnet_label_list.txt` | 모델 내부 원본과 별도 번들 파일의 바이트 해시 일치 확인. 수정하지 않음 |

- `yamnet.tflite`: 4,126,810 bytes, SHA-256 `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`.
- `yamnet-labels.txt`: 6,230 bytes, 521행, SHA-256 `8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051`.
- 가져온 날짜: 2026-11-13. [모델 상세 계약과 검증 경계](docs/models.md), [YAMNet 고지](app/src/main/assets/notices/YAMNet.txt).

고지는 모델 출처와 배포 조건을 추적하는 자료입니다. 실제 워치 추론 성공·정확도·물리 촉각 품질을 의미하지 않습니다.

## 직접 포함한 런타임 라이브러리

현재 Debug/Release의 **실제 해석된 런타임 artifact**에서78개 좌표를 수집했습니다. 테스트 전용 JUnit/Robolectric이나 빌드 도구를 앱 런타임 목록에 섞지 않습니다. 아래 표의 원저자는 Maven POM의 developer 정보이며 세부 조직·URL·원본 POM/부모 POM SHA-256, artifact SHA-256, 빌드 구분과 포함 고지 경로는 [components.json](app/src/main/assets/notices/components.json)에 있습니다. POM 상속도 추적합니다(예: Guava ListenableFuture1.0 → guava-parent26.0-android).

전체 LICENSE/NOTICE 파일은 artifact의 원문 바이트를 [notices/licenses](app/src/main/assets/notices/licenses)에 보존합니다. 같은 바이트는 SHA-256 이름으로 중복 없이 저장하며 component별 원래 entry 이름과 대응시킵니다. APK에도 이 파일·목록과 읽을 수 있는 고지 요약을 넣습니다.

- LiteRT1.4.2의 원문 LICENSE는 Apache-2.0뿐 아니라 **Caffe의 Regents/기여자 저작권 및 BSD 조건**도 포함합니다. 원문 전체를 보존하며 Apache라는 이름만 남겨 뒤의 고지를 지우지 않습니다.
- `datastore-preferences-external-protobuf:1.1.7`의 POM은 BSD-3-Clause입니다. [공식1.1.7 릴리스](https://developer.android.com/jetpack/androidx/releases/datastore#1.1.7)가 가리키는 AndroidX commit `420da5f291449f11801e378c6789a328a06ec6cb`의 [모듈 빌드](https://android.googlesource.com/platform/frameworks/support/+/420da5f291449f11801e378c6789a328a06ec6cb/datastore/datastore-preferences-external-protobuf/build.gradle)와 [버전 표](https://android.googlesource.com/platform/frameworks/support/+/420da5f291449f11801e378c6789a328a06ec6cb/gradle/libs.versions.toml)에서 `protobuf-javalite:4.28.2` 재패키징을 추적했습니다. Google Inc.의 [v28.2 원문](https://github.com/protocolbuffers/protobuf/blob/v28.2/LICENSE)을 [Protobuf-4.28.2-LICENSE.txt](app/src/main/assets/notices/Protobuf-4.28.2-LICENSE.txt)에 보존합니다. AndroidX로 패키지명을 바꾼 라이브러리를 일반 AndroidX Apache 항목으로 뭉뚱그리지 않습니다.
- 그 밖의 표에서 Apache-2.0은 해당 artifact의 POM 선언입니다. 아래 네이티브 하위 구성요소의 라이선스와 동일하다고 단정하지 않습니다.

### 확인된 네이티브 하위 구성요소와 미완료 출처

실제 LiteRT1.4.2 AAR의 arm64 JNI에서 XNNPACK 함수, cpuinfo/gemmlowp 문자열, ruy 소스 경로, EigenForTFLite 심볼 및 flatbuffers 문자열을 확인했습니다. AAR이 제공하는 Apache/Caffe LICENSE만으로 이 구성요소들의 별도 고지를 대신할 수 없어, 다음 원문을 추가했습니다. 원저작권·조건·면책 조항은 각 파일 전체에 보존합니다.

| 구성요소 | 보존한 상위 라이선스 원문 | 바이너리 출처 상태 |
|---|---|---|
| XNNPACK | [Facebook/Google BSD-3-Clause](app/src/main/assets/notices/native/XNNPACK-LICENSE.txt) | LiteRT1.4.2 내부 존재 확인, 정확한 링크 소스 revision 미확정 |
| cpuinfo | [Google/Facebook/Georgia Tech/Marat Dukhan BSD-2-Clause](app/src/main/assets/notices/native/cpuinfo-LICENSE.txt) | 동일 |
| gemmlowp | [Apache-2.0](app/src/main/assets/notices/native/gemmlowp-LICENSE.txt) | 동일 |
| ruy | [Apache-2.0](app/src/main/assets/notices/native/ruy-LICENSE.txt) | 동일 |
| EigenForTFLite / Eigen | [상위 Eigen MPL-2.0 원문](app/src/main/assets/notices/native/Eigen-LICENSE.txt) | 정확한 적용 소스/수정분 및 Source Form 대응 관계 미확정 |
| FlatBuffers | [Apache-2.0](app/src/main/assets/notices/native/FlatBuffers-LICENSE.txt) | LiteRT 내 문자열 확인, 정확한 revision 미확정 |

[native-components.json](app/src/main/assets/notices/native-components.json)은 부모 AAR SHA-256, 관찰한 바이너리 entry/증거와 고지 원문을 가져온 저장소·commit·복사본 해시를 기록합니다. **고지 파일의 조회 commit을 실제 링크된 소스 revision으로 간주하지 않습니다.** 이 목록도 모든 네이티브 전이 의존성의 완전한 목록이 아닙니다.

바이너리에서 `2.22.0-dev0+selfbuilt` 버전 문자열을 관찰했으며 런타임 getter를 호출한 것은 아닙니다. LiteRT 저장소의 v1.4.2 소스 tag는 조회되지 않았습니다. 다른 버전인 v1.4.0/v1.4.1을 이 바이너리의 정확한 소스로 대체하지 않습니다. **정확한 네이티브 출처·나머지 고지와 특히 MPL-2.0 Source Form 제공 대응 관계는 아직 해결되지 않았습니다.** 알려진 고지 누락을 보완했지만 #34 완료/최종 배포 준비 완료를 주장하지 않고 Draft로 유지합니다.

<!-- runtime-components:start -->
| Component | Upstream developer (POM) | Declared license | Scope |
|---|---|---|---|
| `androidx.activity:activity-compose:1.10.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.activity:activity-ktx:1.10.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.activity:activity:1.10.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.annotation:annotation-experimental:1.4.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.annotation:annotation-jvm:1.9.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.arch.core:core-common:2.2.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.arch.core:core-runtime:2.2.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.autofill:autofill:1.0.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.collection:collection-jvm:1.5.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.collection:collection-ktx:1.5.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.animation:animation-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.animation:animation-core-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.animation:animation-graphics-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.foundation:foundation-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.foundation:foundation-layout-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.material:material-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug |
| `androidx.compose.material:material-icons-core-android:1.7.8` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.material:material-ripple-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.runtime:runtime-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.runtime:runtime-saveable-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.ui:ui-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.ui:ui-geometry-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.ui:ui-graphics-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.ui:ui-test-manifest:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug |
| `androidx.compose.ui:ui-text-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.ui:ui-tooling-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug |
| `androidx.compose.ui:ui-tooling-data-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug |
| `androidx.compose.ui:ui-tooling-preview-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.ui:ui-unit-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.compose.ui:ui-util-android:1.8.3` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.concurrent:concurrent-futures:1.1.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.core:core-ktx:1.15.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.core:core-viewtree:1.0.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.core:core:1.15.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.customview:customview-poolingcontainer:1.0.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.datastore:datastore-android:1.1.7` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.datastore:datastore-core-android:1.1.7` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.datastore:datastore-core-okio-jvm:1.1.7` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.datastore:datastore-preferences-android:1.1.7` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.datastore:datastore-preferences-core-android:1.1.7` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.datastore:datastore-preferences-external-protobuf:1.1.7` | The Android Open Source Project | BSD-3-Clause | Debug, Release |
| `androidx.datastore:datastore-preferences-proto:1.1.7` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.emoji2:emoji2:1.4.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.graphics:graphics-path:1.0.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.graphics:graphics-shapes-android:1.0.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.interpolator:interpolator:1.0.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-process:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.2` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.profileinstaller:profileinstaller:1.4.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.savedstate:savedstate-android:1.3.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.savedstate:savedstate-ktx:1.3.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.startup:startup-runtime:1.1.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.tracing:tracing:1.2.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.wear.compose:compose-foundation:1.5.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.wear.compose:compose-material-core:1.5.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `androidx.wear.compose:compose-material3:1.5.0` | The Android Open Source Project | Apache-2.0 | Debug, Release |
| `com.google.ai.edge.litert:litert-api:1.4.2` | Google AI Edge Authors | Apache-2.0 | Debug, Release |
| `com.google.ai.edge.litert:litert:1.4.2` | Google AI Edge Authors | Apache-2.0 | Debug, Release |
| `com.google.guava:listenablefuture:1.0` | Kevin Bourrillion | Apache-2.0 | Debug, Release |
| `com.squareup.okio:okio-jvm:3.4.0` | Square, Inc. | Apache-2.0 | Debug, Release |
| `org.jetbrains:annotations:23.0.0` | JetBrains Team | Apache-2.0 | Debug, Release |
| `org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.9.22` | Kotlin Team | Apache-2.0 | Debug, Release |
| `org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.22` | Kotlin Team | Apache-2.0 | Debug, Release |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0` | Kotlin Team | Apache-2.0 | Debug, Release |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0` | Kotlin Team | Apache-2.0 | Debug, Release |
| `org.jetbrains.kotlin:kotlin-stdlib:2.1.21` | Kotlin Team | Apache-2.0 | Debug, Release |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | JetBrains Team | Apache-2.0 | Debug, Release |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | JetBrains Team | Apache-2.0 | Debug, Release |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | JetBrains Team | Apache-2.0 | Debug, Release |
| `org.jspecify:jspecify:1.0.0` | Kevin Bourrillion | Apache-2.0 | Debug, Release |
<!-- runtime-components:end -->

## 코드 복사와 참고 자료의 구분

| 자료 | 확인한 버전·라이선스 | 울림에서의 사용 범위 |
|---|---|---|
| [ikanam/GameHaptic](https://github.com/ikanam/GameHaptic/tree/2db6c0cd429f0f6e0fcdf57f21e247bc609d4e47) | commit `2db6c0cd429f0f6e0fcdf57f21e247bc609d4e47`, MIT, Copyright(c)2026 ikanam. [확인한 원문](third_party/references/GameHaptic-LICENSE.txt) | 음향→촉각 접근 참고. #8/PR46의 독립 Kotlin 작성 기록 및 현재 DSP/매퍼와 HapticAudioAnalyzer 대조에서 직접 복사본을 확인하지 못함. MIT 코드를 복사했다고 오기하지 않음 |
| [google/audio-to-tactile](https://github.com/google/audio-to-tactile/tree/d3f449fdfd8cfe4a845d0ae244fce2a0bca34a15) | commit `d3f449fdfd8cfe4a845d0ae244fce2a0bca34a15`, [Apache-2.0 LICENSE](https://github.com/google/audio-to-tactile/blob/d3f449fdfd8cfe4a845d0ae244fce2a0bca34a15/LICENSE) | 실시간 촉감화 아이디어·설계 참고. 프로젝트 소스/펌웨어/하드웨어 드라이버를 울림에 포함하지 않음. 이 참고와 별도로 번들 YAMNet은 실제 재배포 자산임 |

울림 `EnvelopeExtractor`는320샘플 RMS/peak와20/120ms 지수 시정수를 사용하는 독립 구현입니다. 비교한 GameHaptic analyzer는 고역 필터·crest factor·동적 ceiling·이벤트별10–72ms 길이와 cooldown을 함께 계산합니다. 울림의 PCM 정규화32768, 연속20ms 계약과 매퍼/스케줄러 역할도 별도로 정의되어 있습니다. 단순 RMS/평활화라는 수학적 아이디어가 비슷하다는 이유로 코드 복사로 기록하지 않습니다.

`git log`의 DSP·촉각 도입/변경 기록, PR46의 작성 출처, 현재 Kotlin 파일 및 위 참조 analyzer를 확인했습니다. Kotlin/Java/C++ 소스에서 GameHaptic 전용 package/클래스/상수와 GPL/LGPL SPDX 표기를 검색해 일치가 없음을 확인했고, 수집된 런타임 POM의 선언은 Apache-2.0 또는 BSD-3-Clause였습니다. **이 확인 범위에서 GPL 프로젝트 코드의 직접 이식은 발견하지 못했습니다.** 모든 외부 바이너리 내부의 원저작권을 법적으로 보증하는 전수 인증이나 개발 PC의 모든 도구가 GPL을 포함하지 않는다는 주장은 아닙니다. 향후 직접 복사하는 코드는 해당 파일·버전·수정 여부와 원저작권/고지를 별도로 기록해야 합니다.

## 빌드·검증 도구

| 도구 | 현재 버전·범위 | 고지/출처 |
|---|---|---|
| Gradle Wrapper/distribution |8.13. Wrapper JAR와 생성 스크립트는 저장소에 있음. 전체 Gradle은 빌드 시 다운로드하며 APK 런타임이 아님 | 설치에 사용한 배포본의 [LICENSE](third_party/build-tools/gradle-8.13-LICENSE.txt)/[NOTICE](third_party/build-tools/gradle-8.13-NOTICE.txt) 보존. 배포 ZIP SHA-256은 wrapper properties에 고정 |
| Android Gradle Plugin |8.13.2, 빌드 전용 | [공식 릴리스](https://developer.android.com/build/releases/agp-8-13-0-release-notes) |
| Kotlin compiler/Compose compiler |2.1.21, 빌드 전용. Kotlin 표준 라이브러리는 위 런타임 표에 별도 포함 | [JetBrains Kotlin](https://github.com/JetBrains/kotlin/tree/v2.1.21) |
| JUnit |4.13.2, 호스트 단위 테스트 전용 | [JUnit4](https://github.com/junit-team/junit4/tree/r4.13.2), EPL-1.0. JAR를 APK에 포함하지 않음 |
| Robolectric |4.17, 호스트 단위 테스트 전용 | [공식 프로젝트](https://github.com/robolectric/robolectric), MIT. JAR를 APK에 포함하지 않음 |

JDK와 Android SDK는 개발 환경의 설치 도구이며 저장소/APK에 배포하지 않습니다. 이 목록이 설치 도구의 별도 이용 조건을 변경하지 않습니다. 모델·런타임·Wrapper의 제삼자 고지가 울림 자체 코드에 자동으로 같은 라이선스를 적용한다는 뜻도 아닙니다.

## 재현·검증

[README](README.md)와 [개발 안내](docs/development.md)의 JDK/SDK/로컬 Gradle/설치 절차를 따릅니다. 현재 기본 main에는 아직 합쳐지지 않은 기능이 있으므로 PR의 검증 대상 브랜치/커밋을 먼저 확인해야 합니다. GitHub Actions는 사용하지 않습니다.

실제 artifact 목록과 고지 재수집(아래 PowerShell 도구는7.2+ 환경):

```powershell
./gradlew.bat -I tools/notice-inventory.init.gradle ullimNoticeInventory '-PnoticeInventoryOutput=../runtime-artifacts.json'
./tools/collect-third-party-notices.ps1 -InventoryPath ../runtime-artifacts.json -GradleCachePath PATH_TO_GRADLE_CACHE/modules-2/files-2.1
./tools/verify-packaged-notices.ps1
```

로컬 artifact 경로가 담긴 입력 JSON은 저장소에 올리지 않습니다. 공개 components.json에는 개인 PC 경로나 인증 정보가 없습니다. 의존성이 바뀌면 목록·부모 POM·원문 고지를 다시 검토하고 Protobuf 같은 별도 고지의 출처도 재확인해야 합니다. 목록 생성이 법적 검토 자체를 대신하지 않습니다.

로컬 빌드·테스트, APK별 고지/모델 바이트 대조와 링크 검사 결과는 [검증 기록](docs/evidence/issue34-packaged-notices.txt)에 있습니다. 검증 도구는 현재 보존한 고지가 APK에 동일하게 들어갔는지 확인하며, 상위 네이티브 출처/고지의 완전성을 판정하지 않습니다. 네이티브 원본 artifact 해시가 바뀌면 수집 도구가 재검토를 요구합니다.

선행 #8/#19/#24가 아직 main 완료 상태가 아니고 네이티브 정확한 소스/고지 및 Eigen Source Form 대응이 미확정이므로, 이 문서 추가만으로 #34를 닫거나 최종 배포 완료를 주장하지 않습니다.
