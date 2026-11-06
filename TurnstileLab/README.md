# NinjaSolver Lab — Turnstile 0.1

Laboratório separado dos plugins Cloudstream do NinjaTV Lab. O diretório não contém `build.gradle.kts`, portanto não é incluído como plugin Android pelo `settings.gradle.kts`.

## Objetivo

Testar, em uma página que controlamos, os estados previsíveis do Cloudflare Turnstile, a validação server-side e a diferença entre uma falha auxiliar de telemetria e uma falha do próprio widget.

**Limite importante:** este laboratório não tenta resolver, contornar, falsificar ou reutilizar tokens de sites de terceiros. Ele usa somente as chaves fictícias oficiais do Cloudflare, destinadas a desenvolvimento e testes.

## Referência de arquitetura

O projeto arquivado [nishatrhythm/CF-Turnstile-Demo](https://github.com/nishatrhythm/CF-Turnstile-Demo) serviu como referência conceitual para a divisão navegador/servidor, seleção de cenários e exibição da resposta Siteverify. O código deste laboratório foi escrito separadamente; não copia o aplicativo Flask daquele repositório. A referência declara licença MIT e está arquivada desde junho de 2026.

## Requisitos

- Python 3.10 ou superior
- Acesso à internet para carregar o widget oficial e chamar o Siteverify
- Telefone e computador na mesma rede Wi-Fi, se quiser testar pelo telefone

## Executar

Na pasta `TurnstileLab`:

```bash
python -m venv .venv
```

Windows:

```powershell
.venv\\Scripts\\Activate.ps1
pip install -r requirements.txt
python app.py
```

Linux/macOS:

```bash
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Abra `http://127.0.0.1:5000` no computador.

### Testar no telefone

1. Deixe o computador e o telefone na mesma rede Wi-Fi.
2. Descubra o endereço IP local do computador.
3. No telefone, abra `http://IP-LOCAL-DO-COMPUTADOR:5000`.
4. Se o firewall perguntar, permita acesso somente na rede privada/local.

O servidor escuta em `0.0.0.0:5000` para permitir esse teste na rede local. Não configure encaminhamento de portas nem publique este servidor na internet. O servidor Flask embutido é apenas para desenvolvimento.

## Cenários disponíveis

As chaves abaixo são as chaves públicas/secretas fictícias publicadas pela Cloudflare. As secretas ficam somente no servidor.

| Cenário | Sitekey de teste | Resultado esperado |
|---|---|---|
| Aprovação visível | `1x00000000000000000000AA` | Aprova |
| Reprovação visível | `2x00000000000000000000AB` | Reprova |
| Aprovação invisível | `1x00000000000000000000BB` | Aprova |
| Reprovação invisível | `2x00000000000000000000BB` | Reprova |
| Desafio interativo | `3x00000000000000000000FF` | Solicita interação |
| Token duplicado/consumido | Sitekey de aprovação + secret de duplicidade | Reprova com erro de duplicidade |

## Simulação de falha auxiliar

O botão **Simular falha auxiliar local (503)** chama apenas a rota local `/simulate-auxiliary-failure`, que retorna uma resposta de erro simulada. Isso permite observar que uma falha de telemetria independente não precisa derrubar a interface do laboratório.

Ela **não** bloqueia nem intercepta `static.cloudflareinsights.com`, `challenges.cloudflare.com` ou qualquer domínio externo. Não use esse resultado para concluir que uma página de terceiros continuará acessível se o Turnstile falhar.

## Segurança

- As chaves secretas de teste são mantidas no servidor.
- O token não é registrado em arquivo nem devolvido pela API.
- Não use chaves reais de produção neste laboratório.
- Não coloque cookies, tokens de sessão, cabeçalhos de autorização ou URLs assinadas nos logs.
- Não exponha o servidor Flask à internet.

## Documentação

- [Cloudflare — Test your Turnstile implementation](https://developers.cloudflare.com/turnstile/troubleshooting/testing/)
- [Cloudflare — Get started](https://developers.cloudflare.com/turnstile/get-started/)
- [Cloudflare — Mobile implementation](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/)
