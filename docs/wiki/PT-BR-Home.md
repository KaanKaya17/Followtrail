# Guia do InstagramTracker

[Türkçe](Home.md) · [English](EN-Home.md) · [Español](ES-Home.md) · [Português (Brasil)](PT-BR-Home.md)

[Instalação](#instalação) · [Arquivo ZIP](PT-BR-Instagram-Data-Archive.md) · [Conexão](#conexão-e-primeiras-listas) · [Monitoramento](#monitoramento-e-notificações) · [Privacidade](#privacidade-e-backups) · [Ajuda](#solução-de-problemas)

O InstagramTracker permite consultar exportações do Instagram, comparar listas e manter o histórico local de alterações. As capturas mostram o Windows com dados de exemplo. Alguns rótulos nas imagens estão em turco.

## Instalação

No Windows, baixe o [ZIP x64 da versão de prévia 0.2.94](https://github.com/KaanKaya17/InstagramTracker/releases/tag/windows-v0.2.94). Extraia o ZIP inteiro e abra **InstagramTracker.exe** na pasta extraída. Mantenha os demais arquivos do pacote na mesma pasta. O executável não tem assinatura Authenticode. A página da versão informa o SHA-256 do ZIP. Para atualizar, escolha **Sair completamente** na bandeja e abra a nova versão em uma pasta separada.

O Android precisa da versão 7.0 ou superior. A prévia assinada **0.1.103** está em [Releases](https://github.com/KaanKaya17/InstagramTracker/releases/tag/android-v0.1.103) e pode ser instalada sobre a 0.1.92. Ela foi instalada em um telefone. O Instagram abriu uma sessão e retornou HTTP 429 durante a leitura do perfil. A validação de uma nova conexão até a conclusão das listas no Android ainda está pendente. A prévia web usa dados de exemplo. [Situação das versões (inglês)](EN-Releases.md).

## Conexão e primeiras listas

Em **Resumo**, abra um ZIP ou vincule a conta para monitoramento. Conclua no Instagram o código ou CAPTCHA solicitado. Se o Android informar que a sessão foi aberta e pedir para concluir a conexão, aguarde o fim da pausa e inicie uma verificação do perfil. Fechar a tela preserva a sessão pendente criptografada. A conexão no Tracker se completa quando o perfil é lido.

A primeira lista completa vira a referência. Compare-a com outra lista completa para encontrar mudanças. Uma leitura parcial preserva a referência anterior. Um nome ausente pode ter mudado, pertencer a uma conta privada ou ter ficado inacessível. Confira a situação da lista antes de interpretar a contagem. Contas conectadas têm históricos individuais e compartilham a pausa do dispositivo.

## Monitoramento e notificações

Em **Perfis salvos → Adicionar pessoa**, procure pelo nome ou cole o link do perfil. Salvar afeta apenas os dados do Tracker. Ative o monitoramento para incluir o perfil nas verificações periódicas. Use favoritos, grupos e notas para organizar as contas. No Windows, **Ctrl+K** ou **/** abre a pesquisa local, **Ctrl+R** atualiza o perfil quando disponível, **1–5** abre as seções principais e **Esc** fecha o painel superior.

**Listas de acompanhamento** mostra seguidores, contas seguidas e comparações disponíveis. Escolha dois perfis para encontrar contas em comum quando houver listas salvas. Em **Aqueles que não te seguem de volta**, use o menu da linha para mover uma conta para as exceções. Ela fica visível em uma seção separada e pode voltar à lista principal.

No Windows, ajuste as verificações e os tipos de aviso em **Configurações**. **Notificações** agrupa alterações da mesma verificação e mostra os valores anteriores e novos. Os controles podem continuar com o computador acordado, conectado e o Tracker na bandeja. Suspensão e desligamento interrompem os controles. No Android, a tarefa em segundo plano é programada em períodos de **15 minutos** e pode atrasar. O intervalo do perfil e a leitura completa das listas têm outras regras.

## Histórico, gráficos e stories

A **Linha do tempo** mostra quando o Tracker observou a mudança. O histórico do perfil compara dois registros de nome, biografia, foto e contagens disponíveis. O **Histórico diário** guarda os primeiros e últimos valores observados, o mínimo, o máximo e o número de verificações. Os dias usam UTC. Dia sem observação fica sem dados.

O arquivo de stories usa um calendário para mostrar o conteúdo salvo. **Verificar stories** lê os stories disponíveis para a conta vinculada. Mídia e menções dependem do conteúdo recebido. Stories ocultos ou expirados e visitantes do perfil não podem ser identificados pelo histórico.

## Finalidade e uso responsável

O InstagramTracker facilita a consulta dos seus arquivos do Instagram e das mudanças em contas às quais você tem acesso autorizado. O aplicativo não envia arquivos ZIP, senhas ou registros de contas à equipe do projeto nem coleta dados de uso em um servidor próprio. Para o monitoramento ao vivo e a verificação, seu dispositivo se conecta ao Instagram/Meta.

Use o Tracker apenas com dados aos quais você tem acesso autorizado. Respeite as regras aplicáveis e a privacidade de outras pessoas. O projeto não apoia acesso não autorizado a contas, assédio nem uso ilegal.

## Privacidade e backups

O Windows protege os dados locais com DPAPI. O Android usa Keystore e AES-GCM. A conexão ao vivo usa serviços do Instagram. O aplicativo mantém a sessão necessária para leitura criptografada e não registra senhas nem códigos no histórico.

O **Histórico portátil** cria um arquivo protegido por senha que abre no Windows ou no Android, com perfis, notas, observações, listas completas e histórico diário. Ele exclui sessões e mídia. O backup do Windows é restaurado com o mesmo usuário do Windows. CSVs não são criptografados. Apagar os dados locais remove os registros da conta selecionada. Arquivos exportados são gerenciados separadamente.

## Solução de problemas

- **Falha ao entrar:** verifique se consegue entrar do zero no Instagram. Informe apenas o resultado e a versão do Tracker.
- **HTTP 429:** consulte **Detalhes da solicitação**, aguarde a pausa e tente a ação uma vez. Trocar de conta ou reinstalar não limpa a pausa.
- **Lista incompleta:** confira a direção, as contagens e a indicação de continuidade. A última lista completa continua disponível para comparação.
- **Sem notificação:** verifique o registro do app, o tipo de aviso, o monitoramento e as permissões do sistema.
- **Serviço local do Windows fechado:** saia completamente, extraia o ZIP inteiro e abra o executável sem executar duas versões ao mesmo tempo.

Ao pedir ajuda, informe a plataforma, a versão, os passos, o erro e o horário aproximado. Não inclua senha, código, cookie ou exportação pessoal. [Problemas no Windows](https://github.com/KaanKaya17/InstagramTracker/issues).
