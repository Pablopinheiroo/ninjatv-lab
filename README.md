# NinjaTV Lab

> Projeto independente desenvolvido **exclusivamente para fins educacionais**, estudo e testes de desenvolvimento de extensões.

O objetivo deste repositório é estudar a estrutura de plugins baseada no ecossistema Cloudstream, a linguagem Kotlin, o sistema de compilação Gradle e a publicação de manifestos por meio do GitHub Actions.

## Finalidade e limites

- Este projeto é destinado ao aprendizado de programação, experimentação e testes de compatibilidade.
- O repositório contém exemplos de código e não hospeda nem distribui filmes, séries, vídeos ou outros conteúdos audiovisuais.
- O plugin de demonstração atual não é uma integração funcional com um serviço de streaming: ele usa `example.com` como endereço ilustrativo e sua função de busca retorna uma lista vazia.
- Qualquer integração futura deverá utilizar conteúdo próprio, de domínio público, licenciado ou serviços cujos responsáveis tenham autorizado expressamente o acesso e a integração.
- Não são objetivo deste projeto contornar controles de acesso, restrições técnicas ou direitos autorais, nem extrair ou redistribuir transmissões de terceiros sem autorização.

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
.\gradlew.bat make makePluginsJson
```

## Manifesto do repositório

O arquivo `repo.json` aponta para o catálogo publicado na branch `builds`:

`https://raw.githubusercontent.com/Pablopinheiroo/ninjatv-lab/builds/repo.json`

A compatibilidade desse manifesto deve ser testada no aplicativo de destino. O fato de o formato ser semelhante ao utilizado pelo Cloudstream não garante, por si só, compatibilidade integral com qualquer aplicativo derivado.

## Créditos e atribuição

A estrutura inicial deste projeto foi baseada no template público [recloudstream/TestPlugins](https://github.com/recloudstream/TestPlugins). Os créditos e as informações de autoria presentes nos arquivos de origem foram preservados. O exemplo de plugin mantém os autores originais indicados no template: **Cloudburst** e **Luna712**.

Este repositório é uma cópia independente para estudo. Alterações aqui realizadas não modificam nem são enviadas automaticamente ao repositório original.

## Licença do template original

O README do template de origem declara que o conteúdo do template é disponibilizado em domínio público, sem condições de uso. Consulte também o repositório original e seus avisos para verificar a licença e a atribuição aplicáveis a cada componente.

