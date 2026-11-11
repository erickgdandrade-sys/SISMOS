# Levanta! ⏰ — o despertador que só para quando você levanta

App Android (8.0 ou mais novo).

## Como funciona

| Toque | O que você pode fazer |
|---|---|
| **1º alarme** | Soneca **ou** provar que levantou |
| **2º alarme** | Soneca (a última) **ou** provar que levantou |
| **3º alarme** | **Sem soneca.** O celular fica travado na tela do alarme: se você sair dela, ela volta em 1 segundo. Dá para **desligar a música**, mas ele **vibra sem parar** até você provar que levantou. Volume no máximo, os botões de volume não funcionam e reiniciar o celular não desliga o alarme. |

**Provar que levantou** = andar com o celular na mão (40 passos por padrão, contados pelo sensor de passos) e depois resolver uma conta de matemática (dá para desligar a conta nas configurações).

### Sincronizar com o Relógio do celular
Com a opção **"Tocar junto com os despertadores do app Relógio"** ligada, o Levanta! descobre o próximo despertador que você criou no Relógio do celular e toca junto com ele. Quando os dois tocarem, desligue o do Relógio normalmente: o do Levanta! continua até você levantar.

> Limite do Android: nenhum app consegue ler a lista inteira de alarmes do Relógio, só o **próximo** que vai tocar. O Levanta! acompanha um de cada vez, automaticamente. Também dá para criar os alarmes direto no Levanta!.

### Emergência
No modo travado há um botão **🆘 Emergência** que abre o discador e libera o celular por 3 minutos. Ele existe para você sempre conseguir pedir ajuda.

## Instalar

1. No celular, abra a página **Releases** deste repositório e baixe o **Levanta.apk** (versão `levanta-latest`).
2. Abra o arquivo e permita "instalar apps desconhecidos" quando o Android pedir.
3. Abra o Levanta! e toque em **Ativar** em cada permissão da lista (notificações, alarme exato, tela cheia, **sobrepor outros apps** — é ela que permite travar o celular —, contar passos e bateria).
4. Use **▶ Testar agora** para ver funcionando.

O APK é gerado automaticamente pelo GitHub Actions (`.github/workflows/levanta-apk.yml`) a cada mudança na pasta `despertador/`. Para compilar no computador: abra a pasta `despertador` no Android Studio, ou rode `./gradlew assembleDebug`.

## Limites que o Android impõe (honestamente)
- Um app comum não consegue bloquear 100% do celular. O Levanta! reabre a tela do alarme em 1 segundo sempre que você sai dela, mas desinstalar o app ou "forçar parada" nas configurações (se você for rápido) ainda são saídas. Celulares Xiaomi, Samsung e Motorola às vezes fecham apps em segundo plano: libere o Levanta! na economia de bateria e, na Xiaomi, ative também "Inicialização automática" e "Exibir janelas pop-up em segundo plano".
- O sensor de passos pode ser enganado sacudindo o celular — por isso existe a conta de matemática depois.
