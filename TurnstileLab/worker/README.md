# NinjaSolver Lab — Cloudflare Workers

Versão online do laboratório Turnstile. Este projeto fica separado dos plugins Cloudstream e não modifica a SuperflixFilmes nem o Donflix.

## Limites

- Usa somente as chaves fictícias oficiais de teste da Cloudflare.
- Não resolve, contorna, falsifica ou reutiliza desafios de sites de terceiros.
- Não recebe cookies, sessões ou tokens de outros sites.
- O endpoint Siteverify é chamado pelo Worker. A chave secreta fica nas configurações do Worker e nunca é enviada à página.
- A simulação HTTP 503 afeta somente uma rota do próprio laboratório.

## Publicar pelo painel Cloudflare (sem terminal)

1. Entre no painel Cloudflare e abra **Workers & Pages**.
2. Escolha **Create application** e conecte uma conta GitHub, se ainda não estiver conectada.
3. Selecione o repositório `Pablopinheiroo/ninjatv-lab`.
4. Selecione a branch `lab/turnstile-workers`.
5. Configure a pasta raiz do projeto como `TurnstileLab/worker`.
6. Use o comando de instalação `npm install` e o comando de publicação `npx wrangler deploy`.
7. Confirme a criação. O Cloudflare deverá publicar o Worker e os arquivos da pasta `public`.

Se a tela de criação oferecer um campo de configuração do Wrangler, use o arquivo `wrangler.toml` que já está nesta pasta.

## Cadastrar as chaves secretas de teste

Depois da primeira publicação:

1. Abra o Worker `ninjatv-turnstile-lab`.
2. Vá a **Settings → Variables and Secrets**.
3. Adicione as três entradas abaixo como **Secret**, não como variável pública:

| Nome | Valor oficial de teste |
|---|---|
| `TURNSTILE_SECRET_PASS` | `1x0000000000000000000000000000000AA` |
| `TURNSTILE_SECRET_FAIL` | `2x0000000000000000000000000000000AA` |
| `TURNSTILE_SECRET_DUPLICATE` | `3x0000000000000000000000000000000AA` |

4. Salve e faça novo deploy se o painel solicitar.

Esses valores são segredos fictícios publicados pela Cloudflare para testes. Não substitua por segredos de produção neste laboratório.

## Testar no telefone e no BTV13

Depois da publicação, abra o endereço `*.workers.dev` do Worker no navegador do aparelho. Não é necessário instalar outro navegador, usar ADB ou conectar o aparelho ao computador.

Teste nesta ordem:

1. Aprovação visível.
2. Reprovação visível.
3. Aprovação invisível.
4. Reprovação invisível.
5. Desafio interativo.
6. Token duplicado/consumido.
7. Simulação de falha auxiliar (503).

O endpoint `/api/health` deve responder com JSON indicando `ok: true`.

## Rotas

- `GET /` — página de testes.
- `GET /api/health` — estado do Worker.
- `GET /api/scenarios` — lista pública dos cenários (sem segredos).
- `POST /api/verify` — valida o token de teste no Siteverify.
- `POST /api/simulate-auxiliary-failure` — retorna 503 simulado.

## Respostas e privacidade

A resposta da API mostra somente o resultado do Siteverify, hostname, data/hora, ação, cdata e códigos de erro. O token enviado e a chave secreta não são devolvidos nem gravados em logs pelo código do Worker.

## Referências oficiais

- Cloudflare — [Test your Turnstile implementation](https://developers.cloudflare.com/turnstile/troubleshooting/testing/)
- Cloudflare — [Workers Static Assets](https://developers.cloudflare.com/workers/static-assets/)
- Cloudflare — [Workers Builds](https://developers.cloudflare.com/workers/ci-cd/builds/)
