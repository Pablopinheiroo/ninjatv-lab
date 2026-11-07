# NinjaTV Lab

Projeto independente para estudo e testes de desenvolvimento de extensões baseadas no ecossistema Cloudstream.

## Quatro catálogos SuperFlix

A branch `lab/turnstile-workers` contém quatro extensões independentes:

| Extensão | Catálogo consultado | Tipo |
|---|---|---|
| Filmes Superflix | SuperFlixAPI `category=filme` | Filmes |
| Séries Superflix | SuperFlixAPI `category=serie` | Séries |
| Animes Superflix | SuperFlixAPI `category=anime` | Animes |
| Doramas Superflix | SuperFlixAPI `category=dorama` | Doramas |

Os catálogos consultam os IDs TMDB disponibilizados pelo endpoint público `/lista`, apresentam páginas de 20 itens e incluem filtros de gênero. A busca tenta o endpoint público de pesquisa e, em seguida, a consulta específica da categoria. Títulos, capas, sinopses, anos e gêneros são aproveitados da resposta quando disponíveis; campos ausentes são complementados pelas páginas públicas do TMDB.

A paginação é feita sobre a lista retornada pela API. A quantidade total e a completude dependem do que a própria SuperFlixAPI disponibiliza no momento da consulta; ainda precisam ser conferidas no NinjaTV com a resposta real do servidor.

## Player compartilhado

Os quatro plugins usam o mesmo arquivo HTML:

`shared/superflix-player.html`

O botão **Reproduzir** envia o tipo de mídia e o ID TMDB para esse arquivo. O link de execução usa raw.githack para servir o HTML com tipo MIME correto; o código-fonte continua hospedado no GitHub. Filmes usam `type=filme`; séries, animes e doramas usam `type=serie`. O player de séries mantém a navegação de temporadas e episódios da própria página SuperFlix.

RAW do player:

`https://raw.githack.com/Pablopinheiroo/ninjatv-lab/lab/turnstile-workers/shared/superflix-player.html`

**Compatibilidade:** a extensão entrega o endereço HTML como link para o aplicativo. A abertura da página e a reprodução incorporada dependem de o NinjaTV tratar esse tipo de link em WebView. A compilação do plugin, por si só, não comprova a reprodução no aparelho; isso precisa ser testado dentro do NinjaTV.

## Fontes e atribuição

- SuperFlixAPI: documentação pública do endpoint `/lista` e dos caminhos `/filme/{id}` e `/serie/{id}`: https://superflixapi.monster/doc
- TMDB: páginas públicas usadas para complementar metadados. Este produto usa metadados do TMDB, mas não é endossado nem certificado pelo TMDB: https://www.themoviedb.org

Os nomes das extensões são rótulos escolhidos para o projeto. Elas não são oficiais nem afiliadas ao site SuperFlix. A presença de metadados não concede licença de exibição ou distribuição audiovisual.

## Diagnóstico Cloudflare

O projeto Turnstile Lab contém cenários de teste oficiais e diagnóstico observacional. Os resultados dos testes com chaves de teste não demonstram que seja possível nem apropriado contornar verificações de sites de terceiros.

## Estrutura e publicação

- `master`: código principal do repositório.
- `builds`: artefatos publicados pelo fluxo de produção.
- `lab/turnstile-workers`: desenvolvimento e validação isolados.
- `lab-builds`: repositório de plugins isolado, publicado pelo fluxo de laboratório.

As alterações desta entrega ficam exclusivamente nas branches de laboratório. A branch `builds` e o fluxo de produção não são modificados.

## Compilação local

Linux/macOS:

```bash
chmod +x gradlew
./gradlew make makePluginsJson
```

Windows:

```powershell
.\gradlew.bat make makePluginsJson
```

Este repositório usa a estrutura de plugins do Cloudstream como base. A compatibilidade integral com cada versão do NinjaTV deve ser verificada no aplicativo de destino.
