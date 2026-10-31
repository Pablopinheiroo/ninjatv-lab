# NinjaTV Lab

> Projeto independente para fins educacionais, estudo e testes de desenvolvimento de extensões.

O repositório estuda a estrutura de plugins baseada no ecossistema Cloudstream, a linguagem Kotlin, o Gradle e a publicação de extensões por GitHub Actions.

## Provider Donflix

O módulo `Donflix` é uma implementação experimental de catálogo. Ele consulta páginas públicas e organiza:

- Filmes, séries e animes.
- Categorias disponíveis no site.
- Pesquisa por título.
- Páginas de detalhes, metadados e episódios identificados no HTML público.

**Estado atual:** o módulo não utiliza WebView. A integração com o player nativo ainda não está concluída; o provider não entrega fontes de vídeo ao player do aplicativo nesta versão.

## Finalidade e limites

- O projeto é destinado ao aprendizado de programação, experimentação e testes de compatibilidade.
- O repositório não hospeda nem redistribui arquivos audiovisuais.
- A disponibilidade pública de uma página ou endereço não significa, por si só, autorização para reproduzir ou redistribuir seu conteúdo.
- Não é objetivo do projeto contornar controles de acesso, restrições técnicas ou direitos autorais, nem extrair ou redistribuir transmissões de terceiros sem autorização.

## Aviso sobre direitos autorais (DMCA)

Este projeto tem caráter educacional e demonstra conceitos de desenvolvimento de extensões que consultam serviços externos. O NinjaTV Lab não hospeda, armazena ou mantém cópias de filmes, séries, animes ou outros arquivos audiovisuais em sua infraestrutura.

As páginas, informações e eventuais mídias permanecem sob responsabilidade e hospedagem dos respectivos serviços de origem. O projeto não declara ser proprietário desses materiais, não concede licença sobre conteúdo de terceiros e não afirma que a disponibilidade pública de um endereço representa autorização para sua utilização.

Se um titular de direitos autorais ou representante identificar neste repositório algum material ou referência que considere irregular, poderá entrar em contato por meio dos canais do projeto no GitHub, indicando a obra, a URL específica e a justificativa do pedido. A solicitação será analisada e, quando pertinente e dentro do controle do projeto, a referência ou integração correspondente poderá ser removida ou desativada.

A remoção de uma referência deste repositório não remove o material do serviço externo que o hospeda. Este aviso descreve a finalidade e o procedimento do projeto; não substitui autorização dos titulares, não garante imunidade jurídica e não determina a legalidade de conteúdos de terceiros.

Este é um projeto independente, sem afiliação, patrocínio ou endosso oficial do NinjaTV ou dos mantenedores do Cloudstream.

## Estrutura do repositório

- `master`: código-fonte, configurações e manifesto do repositório.
- `builds`: arquivos de plugins compilados (`.cs3`), catálogo `plugins.json` e manifesto `repo.json`.

## Compilação

O GitHub Actions executa a compilação quando há alterações na branch `master` ou `main`. O processo prepara o Java e o Android SDK, compila os módulos e publica os arquivos gerados na branch `builds`.

Para uma compilação local em Linux ou macOS:

```bash
chmod +x gradlew
./gradlew make makePluginsJson
```

No Windows:

```powershell
.\\gradlew.bat make makePluginsJson
```

## Manifesto do repositório

O arquivo `repo.json` aponta para o catálogo publicado na branch `builds`:

`https://raw.githubusercontent.com/Pablopinheiroo/ninjatv-lab/builds/repo.json`

A compatibilidade desse manifesto deve ser testada no aplicativo de destino. O formato semelhante ao utilizado pelo Cloudstream não garante, por si só, compatibilidade integral com qualquer aplicativo derivado.

## Créditos e atribuição

A estrutura inicial deste projeto foi baseada no template público [recloudstream/TestPlugins](https://github.com/recloudstream/TestPlugins). Este repositório é uma cópia independente para estudo. Alterações aqui realizadas não modificam nem são enviadas automaticamente ao repositório original.

## Licença do template original

O README do template de origem declara que o conteúdo do template é disponibilizado em domínio público, sem condições de uso. Consulte também o repositório original e seus avisos para verificar a licença e a atribuição aplicáveis a cada componente.
