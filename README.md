# NinjaTV Lab — teste SuperFlix Filmes

Este ciclo do projeto está concentrado em uma única extensão: **Superflix Filmes — Teste API**.

## Meta da versão 1.0

- Consultar o catálogo de filmes pelo endpoint JSON documentado `/lista?category=filme&type=tmdb&format=json&order=desc`.
- Não cadastrar previamente nenhum ID de filme.
- Não inserir filme de demonstração, ID fixo ou amostra artificial.
- Não usar a página HTML como alternativa ao endpoint JSON.
- Exibir o catálogo em páginas de 24 itens, usando os IDs retornados pela API.
- Mostrar uma mensagem de diagnóstico quando a API não retornar JSON utilizável ou quando não houver IDs identificáveis.

O endpoint `/lista` é documentado pelo serviço como a forma de obter listas públicas de IDs do catálogo. Quando a resposta contiver apenas IDs, a primeira versão os apresenta identificados pelo TMDB; o enriquecimento com títulos, cartazes e demais metadados será uma etapa separada, após validarmos a leitura integral da lista.

## Estrutura

- `master`: código-fonte e configurações.
- `builds`: arquivos compilados e manifesto do repositório.

Os módulos antigos de séries, animes, doramas e o teste anterior de filmes continuam guardados no código, mas ficam desativados no build deste ciclo.

## Versões

O Cloudstream define o campo `version` do manifesto como **inteiro**. Por isso, a versão técnica inicial é `1` e a descrição mostra `v1.0`. Nas próximas revisões, o número inteiro será aumentado para que o aplicativo reconheça a atualização; a etiqueta exibida seguirá o padrão solicitado (`v1.1`, `v1.2` ...).

## Compilação

O GitHub Actions compila a extensão quando há alteração na branch `master` ou quando o fluxo é iniciado manualmente em **Actions → Build → Run workflow**.

Para compilação local:

```bash
./gradlew make makePluginsJson
```

## Endereço do repositório

`https://raw.githubusercontent.com/Pablopinheiroo/ninjatv-lab/builds/repo.json`

## Limites

O projeto não hospeda nem redistribui arquivos audiovisuais. A integração deve respeitar os direitos e as condições de acesso do serviço de origem. Esta extensão não contorna desafios, controles de acesso ou restrições técnicas: se a API responder com uma página de bloqueio em vez de JSON, o erro será informado para que o acesso seja resolvido pelo próprio serviço.
