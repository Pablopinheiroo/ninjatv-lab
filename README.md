# NinjaTV Lab

Projeto independente para estudo e testes de desenvolvimento de extensões baseadas no ecossistema Cloudstream.

## Catálogos especializados

A branch `lab/turnstile-workers` contém quatro catálogos independentes, identificados com os nomes solicitados:

| Extensão | Fonte de metadados | Conteúdo |
|---|---|---|
| Filmes Superflix | Cinemeta | Filmes, capas, sinopses, gêneros, ano e pesquisa |
| Séries Superflix | Cinemeta | Séries, capas, sinopses, gêneros, ano e pesquisa |
| Animes Superflix | Jikan API | Animes, capas, sinopses, gêneros, ano e pesquisa |
| Doramas Superflix | TVmaze | Doramas asiáticos, capas, sinopses, gêneros, ano e pesquisa |

**Importante:** os nomes das extensões são rótulos escolhidos para o projeto. Elas não são oficiais nem afiliadas ao site Superflix. Os dados de catálogo vêm das APIs indicadas acima; não são copiados do catálogo Superflix.

## Limites de reprodução

Estes quatro módulos são catálogos de metadados. Eles não extraem URLs de vídeo, não carregam players de terceiros, não contornam Cloudflare/Turnstile e não fornecem reprodução. `loadLinks` retorna `false` intencionalmente. Para assistir a uma obra, use um serviço oficial/licenciado.

O módulo antigo `SuperflixFilmes`, anteriormente descrito como teste experimental, foi convertido em catálogo de metadados Cinemeta. A implementação anterior de WebView/player foi retirada desta branch de laboratório. O módulo `Donflix` foi preservado sem alterações.

## Fontes e atribuição

- **Cinemeta:** catálogo de metadados de filmes e séries. Documentação de integração: https://github.com/Stremio/stremio-addons/blob/master/docs/tutorial/using-cinemeta.md
- **Jikan API:** API pública de dados de anime: https://jikan.moe/
- **TVmaze:** API pública de séries e episódios: https://www.tvmaze.com/api. Os dados públicos são licenciados sob CC BY-SA e exigem atribuição e cumprimento da cláusula ShareAlike. Consulte os termos atuais do serviço.

As capas e sinopses permanecem pertencentes aos respectivos titulares/fontes. A presença de metadados não concede licença de exibição ou distribuição audiovisual.

## Diagnóstico Cloudflare

O projeto Turnstile Lab contém apenas cenários de teste oficiais e diagnóstico observacional. Os resultados dos testes com chaves de teste não demonstram que seja possível nem apropriado contornar verificações de sites de terceiros.

## Estrutura e publicação

- `master`: código principal do repositório.
- `builds`: artefatos publicados pelo fluxo de produção.
- `lab/turnstile-workers`: experimentação e validação isolada.

As alterações desta entrega ficam exclusivamente na branch de laboratório. O fluxo de produção não foi executado e a branch `builds` não foi modificada.

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
