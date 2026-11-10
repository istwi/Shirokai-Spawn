# Validação executada

## Versão 1.1.0 — `/explorar`

Build Java 21 concluído e sete testes JUnit aprovados (os quatro anteriores e três novos para configuração antiga, persistência independente de exploração e rejeição de destino inválido).

Em servidor Fabric 1.21.1 com Multiworld 1.14.2, clientes de protocolo sem o mod verificaram:

- Configuração da versão 1.0 carregada sem apagar nem migrar o JSON manualmente.
- `/explorar` disponível para jogador sem OP, com mensagem clara antes de configurar o destino.
- Administrador salvou `multiworld:shirokai_test`, XYZ 300.5 / 81 / 300.5, yaw 70 e pitch -10 usando `setexplorar`.
- Spawn inicial permaneceu em XYZ 200.5 / 81 / 200.5; a configuração de exploração foi salva separadamente.
- Jogador comum usou `/explorar` a partir do overworld e chegou à dimensão Multiworld com posição e rotação corretas.
- Jogador sem OP tentou `setexplorar` e recebeu bloqueio; o JSON não mudou.
- Dimensão ausente e destino sem chão cancelaram `/explorar` sem mover o jogador.
- `reload` restaurou o destino válido e o comando voltou a funcionar.
- O arquivo de UUIDs de primeiro acesso permaneceu idêntico durante os comandos e falhas.

Esses testes usaram clientes de protocolo e classes de desenvolvimento, com as mesmas limitações de cliente gráfico e LuckPerms descritas abaixo. Evidências: `docs/evidencias-explorar.txt`.

## Versão 1.0.0 — testes originais

Data: 10/11/2026. Ambiente isolado: Minecraft 1.21.1, Fabric Loader 0.16.10, Fabric API 0.116.1+1.21.1, Java 21; Multiworld 1.14.2 com Fantasy 0.6.3+1.21 na segunda etapa.

## Build e testes automatizados

`gradle build --no-daemon`: concluído com sucesso. JAR remapeado e Fabric Permissions API incluída. O Gradle Wrapper usa Gradle 8.10.2 e verifica o SHA-256 da distribuição.

Quatro testes JUnit executados e aprovados:

- Registro de jogadores, pendências e resets preservado após leitura, substituição e nova leitura.
- JSON danificado recusado sem sobrescrever seu conteúdo.
- Leitura do esquema original contendo apenas `players`.
- Registro com conjunto nulo recusado.

## Cenários no servidor

Foram usados clientes de protocolo `minecraft-protocol` 1.21.1, sem Fabric ou Shirokai Spawn no cliente. Os resultados abaixo foram observados em conexões reais ao servidor de desenvolvimento executado pelo Fabric Loom.

| Cenário obrigatório | Resultado observado |
| --- | --- |
| 1. Jogador novo no destino configurado | Aprovado: FreshOne em 100.5 / 81 / 100.5; MultiworldNew em 200.5 / 81 / 200.5 na dimensão Multiworld |
| 2. Reconexão sem novo teleporte | Aprovado: FreshOne foi movido para 120.5 / 81 / 120.5 e manteve essa posição na reconexão |
| 3. Jogador anterior à instalação protegido | Aprovado em simulação: UnsafeOne tinha `.dat` salvo e nenhum registro do mod; no reinício, foi protegido e manteve 5.5 / -60 / 7.5 |
| 4. Definir spawn no Multiworld | Aprovado: dimensão criada com `/mw create`, administrador executou `/shirokaispawn set`; JSON preservou `multiworld:shirokai_test`, XYZ, yaw=45 e pitch=10 |
| 5. Persistência após reinício | Aprovado: registro com 4 UUIDs e configuração XYZ mantidos no segundo início do servidor |
| 6. Dimensão ausente | Aprovado: `missing:world` cancelou o teleporte, preservou PendingOne em `pending`; nova conexão com destino válido concluiu |
| 7. Reconexão rápida | Aprovado: RapidOne desconectou antes do atraso de 20 ticks nominais e reconectou; apenas um teleporte bem-sucedido no log |
| 8. Jogador sem OP | Aprovado: jogadores novos sem OP receberam o teleporte; MultiworldNew recebeu árvore de comandos sem `shirokaispawn` |
| 9. Comandos administrativos | Aprovado: set, info, reload, test, reset e setplayer; `test` restaurou posição e rotação na dimensão Multiworld |
| 10. Ausência do mod no cliente | Aprovado no protocolo: bots sem Fabric/mod conectaram e receberam o teleporte entre dimensões; não foi executado cliente gráfico Minecraft |

Também foi verificado que um destino sem chão cancela o teleporte sem marcar sucesso. Consulte `docs/evidencias-testes.txt` para as posições, registros e verificações relevantes.

## Limites da validação

- LuckPerms não foi instalado no servidor de teste. Integração compilada contra Fabric Permissions API 0.3.1; fallback OP foi exercitado com o administrador.
- Renascimento opcional, camas/âncoras e `overridePersonalRespawn` ainda não receberam teste de integração.
- Não foram simulados queda de energia, disco cheio, diretório sem permissão, mundos muito grandes ou todos os blocos perigosos.
- Os bots emitiram `PartialReadError` ao interpretar o pacote de receitas do vanilla. Continuaram conectados, confirmaram teleportes e executaram comandos; os resultados foram cruzados com posição e persistência no servidor. Isso limita a validação do cliente de protocolo, não constitui teste de cliente gráfico.
- Os testes de integração usaram as classes de desenvolvimento do mesmo código compilado. O JAR remapeado foi inspecionado para metadados, refmap e dependência embutida; não foi iniciado um segundo servidor de produção a partir desse JAR.

## Repetição manual

Em um servidor de teste com backups, configure uma plataforma segura pelo comando `set`, conecte um UUID novo, mova esse jogador e reconecte. Reinicie e confira os JSONs. Para simular instalação em servidor antigo, crie previamente os dados vanilla de um jogador sem registro do mod. Teste uma dimensão Multiworld carregada e um ID inexistente. Para um teste gráfico completo, use um cliente vanilla 1.21.1 sem o mod. Para LuckPerms, remova OP de um administrador, conceda `shirokaispawn.admin` e verifique comandos; depois negue a permissão e confira o bloqueio.
