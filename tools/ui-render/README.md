# 화면 렌더 도구 (에뮬레이터 없이 PNG로 확인)

앱의 화면 코드(`app/.../ui`)와 `core`를 그대로 Compose Desktop(JVM)으로 컴파일해 PNG로 뽑아요.
에뮬레이터를 켜지 않고 훈련 장면·코치 화면의 모양을 확인할 때 써요. 앱 빌드에는 들어가지 않는 별도 Gradle 프로젝트예요.

```bash
./gradlew -p tools/ui-render -q run                      # 전부 → tools/ui-render/out/*.png
./gradlew -p tools/ui-render -q run --args="t_rain c_"   # 이름에 이 글자가 든 것만
python3 tools/ui-render/sheet.py sheet.jpg 2 780 tools/ui-render/out/t_*.png   # 여러 장을 한 장으로
```

- 창 크기 1682×1210, 밀도 1.25(에뮬레이터 가로 창과 같음). 세로는 835×1210.
- `t_*`는 훈련 장면(`UiSamples.trainingAt`의 시연 시각), `c_*`는 코치 화면, `h_home`은 홈.
- `android.util.Log`와 `voice.Persona`는 `src/main/kotlin`의 대역을 써요. 이름을 바꾸면 대역도 같이 바꿔야 해요.
- 글꼴·글자 폭은 데스크톱 기본 글꼴이라 실기와 조금 달라요. 프레임 속도·CPU는 여기서 잴 수 없어요.
