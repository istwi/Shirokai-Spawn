# Shirokai Spawn

Mod exclusivamente de servidor para **Minecraft 1.21.1 / Fabric / Java 21**.
Novos jogadores recebem um teleporte inicial para uma dimensão carregada. Clientes não precisam instalar o mod.

## Instalação

1. Pare o servidor e faça backup do mundo.
2. Instale Fabric Loader 0.16.10 ou superior e Fabric API compatível com 1.21.1.
3. Coloque `shirokai-spawn-1.1.0.jar` em `mods/` (não use o JAR `-sources`).
4. Inicie o servidor. Entre como administrador, prepare uma plataforma segura no mundo desejado e execute `/shirokaispawn set`.
5. Use `/shirokaispawn test` para verificar o destino.

Fabric Permissions API vem incluída no JAR. LuckPerms é opcional: quando instalado, configure `shirokaispawn.admin`. Sem um provedor de permissões, o fallback é OP nível 2. Jogadores comuns não precisam de permissão.

## Exploração: `/explorar`

O jogador usa `/explorar` para se teleportar a um ponto fixo configurado pelo administrador. Não exige OP nem permissão especial e pode ser usado novamente a qualquer momento.

Para configurar, entre no mundo/região desejados, fique sobre uma plataforma segura e execute:

```text
/shirokaispawn setexplorar
```

Esse comando exige `shirokaispawn.admin` (fallback OP nível 2). Salva a dimensão completa, XYZ, yaw e pitch no campo `explorar` de `config/shirokaispawn.json`. Funciona também com dimensões carregadas do Multiworld. A região é representada por esse ponto de chegada fixo; não há sorteio de coordenadas ou seleção de área.

O destino de exploração é independente do spawn inicial. O comando não altera os UUIDs processados e não substitui o primeiro teleporte automático. Sem destino configurado, mundo indisponível ou posição insegura, o jogador recebe uma mensagem e permanece onde está. O destino usa as mesmas verificações de segurança do spawn inicial.

Não é necessário apagar ou recriar o JSON da versão 1.0.0: configure o destino com `setexplorar`. Ele é salvo imediatamente e também pode ser alterado pelo JSON e `/shirokaispawn reload`. `enabled` controla os teleportes automáticos; os comandos explícitos `/explorar` e `test` continuam disponíveis.

## Comandos

| Comando | Ação |
| --- | --- |
| `/explorar` | Teleporta o próprio jogador ao destino de exploração; disponível a todos |
| `/shirokaispawn setexplorar` | Salva o destino independente de exploração; exige administrador jogador |
| `/shirokaispawn set` | Salva dimensão completa, XYZ, yaw e pitch atuais; exige jogador |
| `/shirokaispawn info` | Exibe o destino |
| `/shirokaispawn reload` | Recarrega e valida o JSON; mantém a configuração anterior se inválido |
| `/shirokaispawn test` | Testa o teleporte sem alterar o registro inicial; exige jogador |
| `/shirokaispawn reset <jogador>` | Libera um novo teleporte no próximo login |
| `/shirokaispawn setplayer <jogador>` | Marca como processado, sem mover |

`reset` e `setplayer` aceitam jogadores online ou perfis conhecidos pelo cache do servidor. Não inventam UUIDs a partir de nomes. Console pode usar os comandos administrativos exceto `set`, `setexplorar` e `test`. `/explorar` exige jogador.

## Configuração

Arquivo: `config/shirokaispawn.json`.

```json
{
  "enabled": true,
  "dimension": "minecraft:overworld",
  "x": 0.5,
  "y": 80.0,
  "z": 0.5,
  "yaw": 0.0,
  "pitch": 0.0,
  "firstJoinOnly": true,
  "teleportDelayTicks": 1,
  "protectExistingPlayers": true,
  "teleportOnRespawn": false,
  "overridePersonalRespawn": false,
  "explorar": {
    "dimension": "multiworld:exploracao",
    "x": 100.5,
    "y": 81.0,
    "z": 100.5,
    "yaw": 0.0,
    "pitch": 0.0
  }
}
```

O campo `explorar` só é adicionado ao salvar um destino; o exemplo acima pressupõe uma dimensão existente e uma plataforma segura.

O destino padrão pode estar no ar: configure uma plataforma antes de admitir jogadores novos. O mod cancela destinos inseguros, em vez de procurar outro local ou criar blocos.

`teleportDelayTicks`: 0..1200, convertido em tempo nominal de ticks (50 ms cada, mínimo de um tick). Uma tarefa temporizada envia uma única tarefa à thread principal após JOIN. Em servidor com lag, não equivale a uma contagem exata de ticks. Não há listener permanente de ticks ou polling. Desconexão cancela a tarefa e invalida seu identificador de sessão.

`firstJoinOnly` e `protectExistingPlayers` precisam permanecer `true`: valores `false` são rejeitados para preservar os requisitos de proteção. `teleportOnRespawn` permite teleporte após morte; com `overridePersonalRespawn=false`, qualquer cama/âncora configurada tem prioridade. `overridePersonalRespawn=true` permite explicitamente sobrescrever essa prioridade. Renascimento não altera os registros de primeiro acesso.

## Persistência e jogadores antigos

Arquivo: `<mundo principal>/shirokaispawn/players.json`. `players` contém UUIDs protegidos/processados. `pending` registra jogadores comprovadamente novos aguardando um teleporte bem-sucedido. `reset` contém exceções administrativas para o próximo login. Campos adicionais são necessários para preservar tentativas e resets após reinícios.

Na inicialização, arquivos UUID `.dat` e `.dat_old` de `playerdata/` são protegidos, exceto tentativas pendentes e resets administrativos. Antes de o fluxo vanilla de conexão poder salvar o novo jogador, uma injeção em `PlayerManager.onPlayerConnect` classifica o UUID. Arquivos já existentes nunca são tratados como jogadores novos. Falha de leitura, identificação ou persistência desativa o teleporte automático e registra erro; corrija a causa e reinicie. `reload` só recarrega configuração, não reativa um registro danificado.

Não apague `players.json` para resetar jogadores. Use o comando. Gravações usam arquivo temporário, sincronização dos dados e substituição atômica quando disponível. Uma queda entre teleporte e gravação pode causar nova tentativa; garantir transação atômica entre estado vanilla e JSON não é possível. Em falha de gravação o mod interrompe os próximos teleportes automaticamente.

## Multiworld e segurança

O destino é resolvido pelo `RegistryKey<World>` do servidor. Qualquer dimensão registrada e carregada pode ser usada, sem dependência direta de Multiworld. O mod não cria/apaga mundos nem modifica Multiworld. Dimensão ausente, destino ocupado, fluidos, falta de chão, borda e perigos comuns cancelam o teleporte; um jogador novo continua pendente até o próximo login.

A validação exige espaço para o corpo e chão sólido. Ela não impede perigos construídos ao redor do destino, entidades hostis ou alterações futuras nos blocos. Configure uma área segura. O chunk de destino pode ser carregado sincronamente durante o teleporte.

## Compilar

```sh
./gradlew build
```

Requer JDK 21 e acesso aos repositórios Minecraft, Fabric, Maven Central e Nucleoid. Resultado: `build/libs/shirokai-spawn-1.1.0.jar`.

Consulte `TESTES.md` para os testes realmente executados e os cenários ainda pendentes.
