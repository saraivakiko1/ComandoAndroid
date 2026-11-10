# Comando Projetor

App Android que controla o Android TV do projetor (ETOE E3 Pro) pela rede, usando o
mesmo protocolo da app oficial Google TV (Android TV Remote v2).

A ligação fica aberta enquanto a app está em uso, por isso cada tecla é enviada de imediato.

## Abrir e instalar

1. Abre a pasta `ComandoProjetor` no Android Studio (Ladybug ou mais recente).
2. Espera que o Gradle sincronize (descarrega as dependências na primeira vez).
3. Liga o telemóvel por USB com a depuração USB ativa e carrega em **Run ▶**.
   Para um APK: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## Usar

1. Telemóvel e projetor na **mesma rede Wi-Fi**.
2. Na app, toca em **Procurar na rede** (ou escreve o IP do projetor, que está em
   Definições → Rede e Internet no projetor).
3. Toca em **Emparelhar**. Aparece um código de 6 caracteres no projetor: escreve-o na app.
4. Fica guardado; das próximas vezes a app abre logo no comando.

Os botões de volume do telemóvel também controlam o volume do projetor.

## Se não ligar

- "Não consegui ligar a …": confirma o IP. Se estiver certo, o projetor pode não ter o
  serviço de comando do Android TV (portas 6466/6467). Nesse caso a alternativa é ADB por Wi-Fi.
- "O projetor fechou a ligação": usa o menu ⋮ → **Emparelhar de novo**.

## Estrutura

- `net/Proto.kt` – codificação protobuf mínima e enquadramento das mensagens
- `net/CertManager.kt` – certificado TLS do cliente (gerado uma vez)
- `net/Pairing.kt` – emparelhamento (porta 6467)
- `net/RemoteConnection.kt` – envio de teclas e respostas a pings (porta 6466)
- `net/Discovery.kt` – procura na rede (mDNS `_androidtvremote2._tcp`)
- `RemoteViewModel.kt`, `Ui.kt`, `MainActivity.kt` – interface em Jetpack Compose
