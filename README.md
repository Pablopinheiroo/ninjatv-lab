# NinjaTV Lab

Projeto independente para estudo e testes de desenvolvimento de extensões baseadas no ecossistema Cloudstream.

## Catálogos especializados

A branch `lab/turnstile-workers` contém quatro catálogos independentes:

| Extensão | Fonte de metadados | Organização |
|---|---|---|
| Filmes Superflix | Cinemeta | Gêneros do catálogo Superflix, pesquisa e páginas de 100 títulos |
| Séries Superflix | Cinemeta | Gêneros do catálogo Superflix, pesquisa e páginas de 100 títulos |
| Animes Superflix | Kitsu API | Categorias próprias de anime e paginação por 20 itens |
| Doramas Superflix | TVmaze | Filtros por país/idioma e gêneros; índice em páginas de 250 registros |

Os rótulos de gênero de filmes e séries seguem a organização pública observada nas páginas de catálogo do Superflix. Os metadados continuam sendo obtidos de fontes independentes, não do catálogo de títulos do Superflix.

**Importante:** os nomes das extensões são rótulos escolhidos para o projeto. Elas não são oficiais nem afiliadas ao site Superflix. A disponibilidade e a quantidade de títulos variam de acordo com a base de metadados de cada fonte; isso não é uma cópia integral do acervo do Superflix.

## Limites de reprodução

Estes quatro módulos são catálogos de metadados. Eles não extraem URLs de vídeo, não carregam players de terceiros, não contornam Cloudflare/Turnstile e não fornecem reprodução. `loadLinks` retorna `false` intencionalmente. Para assistir a uma obra, use um serviço oficial/licenciado.

O módulo antigo `SuperflixFilmes`, anteriormente descrito como teste experimental, foi convertido em catálogo de metadados Cinemeta. A implementação anterior de WebView/player foi retirada desta branch de laboratório. O módulo `Donflix` foi preservado sem alterações.

## Fontes e atribuição

- **Cinemeta:** catálogo de metadados de filmes e séries. A integração usa paginação e filtro de gênero do protocolo de catálogos do Cinemeta.
- **Kitsu:** API pública de metadados de anime: https://kitsu.docs.apiary.io/
- **TVmaze:** API pública de séries e episódios: https://www.tvmaze.com/api. Os dados públicos são licenciados sob CC BY-SA e exigem atribuição e cumprimento da cláusula ShareAlike. Consulte os termos atuais do serviço.

As capas e sinopses permanecem pertencentes aos respectivos titulares/fontes. A presença de metadados não concede licença de exibição ou distribuição audiovisual.

## Diagnóstico Cloudflare

O projeto Turnstile Lab contém apenas cenários de teste oficiais e diagnóstico observacional. Os resultados dos testes com chaves de teste não demonstram que seja possível nem apropriado contornar verificações de sites de terceiros.

## Estrutura e publicação

- `master`: código principal do repositório.
- `builds`: artefatos publicados pelo fluxo de produção.
- `lab/turnstile-workers`: experimentação e validação isolada.
- `lab-builds`: repositório de plugins isolado, publicado pelo fluxo de laboratório.

As alterações desta entrega ficam exclusivamente nas branches de laboratório. O fluxo de produção não foi executado e a branch `builds` não foi modificada.

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

Este repositório usa a estrutura de plugins do Cloudstream como base. Compatibilidade integral com cada versão do NinjaTV precisa ser verificada no aplicativo de destino.
