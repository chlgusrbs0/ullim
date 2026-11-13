# APK 고지와 실제 artifact/JNI 연결 검사 (#34)

소스 `6dbea9452218e706f7107078c58154d46904d0a2` 및 검토 보완 `56eb3003fad875ffe3d1a6768ccf29e4b0cd4e85`, 기존 PR71/79de7a2 기반. 제품 코드·의존성 버전·모델/레이블·워치 설치본은 바꾸지 않았다. **네이티브 전체 출처/고지 및 Eigen Source Form은 여전히 미확정이며 #34 완료 증거가 아니다.**

기존 도구가 고지/모델이 그대로인 상태의 다른 JNI 바이너리도 받아들이는 것을 확인했다. 두 기존 PR APK와 새 런타임 목록을27초에 생성했고, 변경/누락/추가/중복 JNI 및 좌표/artifact 파일 변경의 임시 fixture 여섯 개를 기존 검사가 모두 받아들이는 의미적 RED를 재현했다. fixture는 자체 임시 파일만 고치고 제거하며 설치·배포하지 않는다.

## 보완과 검토

- collector schema2에서 실제 artifact별 JNI entry의 APK 경로/바이트 수/SHA256을 기록한다. 원래 고지 원문을 바꾸지 않고 native completeInventory=false/미확정 revision을 보존했다.
- verifier는 같은 빌드에서 새로 만든 Gradle 런타임 목록의 좌표·이름·구성·현재 artifact 파일 해시와 기록을 비교한다. 각 APK의 JNI 전체 목록·각 파일 바이트도 대조해 누락/추가/중복/교체를 거절한다. 고지14개와 모델/레이블 검사도 유지한다.
- 실제 Gradle 목록154행에는 동일 core1.15 artifact가 중복됐다. 기존 collector처럼 정확히 같은 좌표/이름/구성/해시만 정규화하면152개이며,78개 component다. 네이티브 부모는 LiteRT1.4.2, datastore-core-android1.1.7, graphics-path1.0.1이며 각각4개 ABI, 각 APK총12개 JNI entry다.
- 독립 읽기 전용 검토가 실제 두 APK와 resolved archive12개 파일의 길이/해시를 다시 확인했다. 빈 APK/variant 배열이 성공으로 반환되는 Important1건을 의미적 RED로 재현하고 최소1개 쌍을 요구하도록 한 차례 수정했다. 재검토를 반복하지 않았다.
- 남은 경미 항목: ValidateSet이 Debug/Release 혼합 대소문자를 허용하지만 내부의 정확한 구성명 비교에서 거절한다. 문서대로 소문자 debug/release를 사용해야 한다. 이번 필수 보완에는 포함하지 않았다.

## 실행 결과

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug -I tools/notice-inventory.init.gradle ullimNoticeInventory '-PnoticeInventoryOutput=../runtime-artifacts.json'
./tools/collect-third-party-notices.ps1 -InventoryPath ../runtime-artifacts.json -GradleCachePath PATH_TO_GRADLE_CACHE/modules-2/files-2.1
# 목록을 바꿨으면 두 APK를 다시 조립한 뒤 아래를 실행한다.
./tools/verify-packaged-notices.ps1 -InventoryPath ../runtime-artifacts.json
./tools/test-packaged-notices.ps1 -InventoryPath ../runtime-artifacts.json
```

목록 생성 후 고지 자산이 바뀌었으므로 두 APK를 다시 조립했다. 전체 **168tests/실패·오류·skip0 및 test/build/lint/release 성공37초**. 검토 보완 후 같은 Gradle 검사 성공2초/104tasks(제품 입력이 바뀌지 않아102개 up-to-date), 두 APK와 일곱 부정 fixture를 실제 재검사했다.

| 산출물 | bytes | SHA256 |
|---|---:|---|
| Debug APK |52767863|ac83ee4c0173a1101b9f289731af55a12f583431c7fc95ebeff5d48c735b864b|
| unsigned Release APK |45471427|b73fa985e146bdc92b605e31daedb547a0a42775bde644838871e66362b9c03e|

두 APK 모두 고지14개/런타임 artifact 구성152개/JNI12개 일치, 모델·레이블 해시 보존, completeNativeProvenance=false다. 일곱 실패 fixture(empty-input/changed-native/missing-native/extra-native/duplicate-native/coordinate-drift/artifact-drift)는 모두 예상 이유로 거절됐다. 빈 입력만 허용되던 검토 RED와 여섯 드리프트 RED는 각각 확인했다.

## 판단의 한계

#1/#8/#19/#24는 현재 main 완료지만, PR71에는 아직 미병합 누적 제품 기능도 있다. LiteRT 내부 정확한 링크 소스/전체 전이 구성요소/Eigen Source Form은 위 해시 일치와 별개로 미확정이다. 이 파일 검사만으로 출처 의무를 누락한 배포를 허용하지 않는다. native-components.json은 바꾸지 않았고 SHA256 `eb08c628fe9a321420cf211e68f09d4c12860bb266247a0d0ccb491d6231766c` 그대로다.

variant와 런타임 목록은 호출자가 제공한 로컬 빌드 관찰이다. 서명된 빌드 증명이나 DEX 전체 출처/variant 자동 판정까지 검증한다는 주장은 하지 않는다. 잘못 연결한 입력은 빌드를 잘못 표시할 수 있다. 워치 실행·촉각·OS 검증도 수행하지 않았고 현재 정상 설치본/보정/설정을 보존했다. 이 도구의 통과를 실제 하드웨어 동작이나 #34의 전체 라이선스 합격으로 바꾸지 않는다. 자체 프로젝트 LICENSE 추가/Actions/원본 오디오 취급은 없다.
