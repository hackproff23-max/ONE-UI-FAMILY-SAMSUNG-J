# ONE UI

Launcher Android (`org.samsung.oneui`) com tela inicial e gaveta de apps vertical.

- **Tela inicial:** relógio, data, apps fixados e dock (até 5 apps).
- **Gaveta:** deslize para cima na tela inicial (ou toque na barrinha). A gaveta acompanha o dedo; deslize para baixo com a lista no topo para fechar. Tem busca por nome.
- **Segurar um app:** fixar na tela inicial, fixar no dock, informações do app e desinstalar.
- O botão Voltar fecha a gaveta; o botão Home volta para a tela inicial.
- **Pacotes de ícones** (formato ADW/Nova/Apex): segure em um espaço vazio da tela inicial > "Pacote de ícones". Apps que o pacote não cobre são redesenhados no formato dos outros (usa iconback/iconmask/iconupon do pacote; sem isso, detecta se os ícones do pacote são círculo, squircle ou quadrado arredondado). Também dá para forçar o formato em "Formato dos ícones sem tema".

## Build
O GitHub Actions gera o APK a cada push (`.github/workflows/build.yml`). Baixe em Actions > Build APK > Artifacts > ONE-UI-APK.

Local: `./gradlew assembleDebug` (JDK 17). Requisitos: Android 8.0+ (minSdk 26).
