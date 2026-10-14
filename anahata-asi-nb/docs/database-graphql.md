# Database Toolkit: contrato GraphQL de referência

A Database é a primeira implementação de referência. Os demais toolkits continuam
com suas ferramentas Java atuais; a infraestrutura genérica não os migra para
GraphQL nem habilita comandos automaticamente. Motor graphql-java 24.3 embutido,
Java 21, NetBeans RELEASE310; nenhum servidor HTTP ou novo módulo de testes.

## Uso pelo modelo

A IA descobre apenas `Database.schema`, `Database.query` e `Database.mutate`.
`schema()` fornece o SDL efetivamente instalado; [database-schema.graphql](database-schema.graphql)
registra o contrato no repositório. Query e Mutation exigem canais separados e
exatamente uma operação; fragments, aliases, variáveis e directives nativos são
suportados. Subscription não é suportada.

| Operação | Campo GraphQL |
|---|---|
| Descobrir conexões | Query.connections |
| Inspecionar metadados | Query.connection.metadata |
| Listar tabelas | Query.connection.tables |
| Inspecionar colunas | TableInfo.columns |
| Conectar | Mutation.connect |
| Desconectar | Mutation.disconnect |
| Executar SQL com resultado | Mutation.executeQuery |
| Executar SQL sem resultado tabular | Mutation.executeStatement |

Query continua
oferecendo PK e cada direção FK sob demanda. Query não aceita SQL arbitrário.
SQL explícito, inclusive `executeQuery`, é Mutation: o texto SQL pode ter efeitos,
independentemente do nome do método ou de um prefixo SELECT. Isto não é um parser,
ORM, gerador de SQL ou autorização por campo.

## Autorização e destino

As três ferramentas têm permissão padrão PROMPT. A aprovação existente se aplica
ao documento e às variáveis da chamada inteira. `mutate` exige o contexto real de
execução da ferramenta; uma chamada Java direta é rejeitada. A resposta verifica
novamente a permissão e o estado ativo apenas nos toolkits que
optaram explicitamente por comandos GraphQL. O fluxo dos outros toolkits é preservado.

O preflight nativo verifica todos os comandos efetivamente selecionados e seus
argumentos coercidos antes do primeiro efeito. Directives excluem comandos;
fragments e aliases não contornam a validação. Falha de argumento ou identidade
em um comando posterior impede o início dos anteriores. O handle exato é fixado
por requisição. Prefira o `id` opaco retornado por connections. Query.connection
exige exatamente um `id` ou `name`; comandos aceitam id ou nome registrado exato.
Destino ausente, ambíguo e inativo são erros distintos, sem conexão alternativa.
Rótulos contendo JDBC/credenciais são suprimidos na descoberta; URL/user/password
não são campos do schema nem contexto automático. O hash é um identificador,
não um segredo ou mecanismo de autorização.

`catalog`/`schema` em comandos SQL são asserções opcionais sobre a conexão ativa;
jamais chamam setCatalog/setSchema ou trocam o destino. Não tornam SQL arbitrário
um sandbox: SQL autorizado pode referenciar outras bases ou explicitamente
alterar o contexto. Ao usar Mutation.connect, a IDE pode abrir seu diálogo normal;
nenhuma descoberta ou SQL abre diálogo automaticamente.

## Efeitos, seleção e erros

Campos raiz de Mutation executam serialmente pela regra GraphQL. Não há transação
compartilhada, commit/rollback automático, alteração de autocommit ou retry.
Um comando pode ter produzido efeitos mesmo se a coleta/serialização/deadline
falhar depois. Verifique o estado antes de repetir. Outros comandos válidos podem
continuar depois de erro em um campo nullable; `data` e `errors` são obrigatórios
para interpretar o resultado. Status EXECUTED da ferramenta significa que a
chamada retornou, não que todos os campos GraphQL tiveram sucesso.

A seleção determina aquisição real de dados, não apenas projeção:

- tables { items { tableName } } não busca columns/PK/FK.
- columns { columnName } não carrega PK; columns.primaryKey e table.primaryKeys
  compartilham uma carga por identidade completa e requisição.
- Imported e exported FK são operações independentes; só itens paginados têm filhos.
- executeQuery { success } executa SQL e fecha o cursor, sem next/getMetaData/getObject.
- Contagem/hasNextPage podem iterar sem extrair células ou metadados de coluna.
- columns { label } usa getColumnLabel, sem getColumnName/getColumnType nem leitura das linhas.
- rows { cells { columnIndex } } precisa de columnCount, sem getObject.
- Atributos já obtidos dos DTOs usam getters. Drivers podem realizar seu próprio
  prefetch; não prometemos ausência de trabalho interno do driver.

SQL retorna estrutura posicional: columns[index,label,name,...] e
rows[index,cells[columnIndex,value,truncated,encoding]]. Índices de coluna são
1-based; índices de linha, 0-based. Labels duplicados não sobrescrevem células.
SqlValue é apenas o escalar de uma célula (número, boolean, texto ou null), não um
blob JSON do resultado. Binário usa base64; textos/LOBs podem ser truncados com
flag explícita. Objetos arbitrários do driver não são enviados; use CAST em SQL.
O serializer local conserva campos selecionados com null, sem mudar o mapper dos
outros toolkits. A conversão e o registro das variáveis, incluindo edições na chamada, preservam null explícito
e valores nulos aninhados: null não se transforma em variável omitida/default.
Chamadas GraphQL rejeitadas no protocolo exigem uma nova chamada, sem executar argumentos parciais.
O DTO de conexão contém somente atributos públicos, incluindo o id opaco;
URL e usuário não são armazenados nele. O hint textual recomenda inspecionar data/errors.

Erros SQL usam categorias seguras e uma referência para correlação local:
SqlTableNotFound, SqlColumnNotFound, SqlConnectionFailure, SqlPermissionDenied,
SqlTimeout ou SqlExecutionFailed. SQLState varia entre drivers; categorias genéricas
continuam possíveis. Mensagem do driver, SQL, URL e stacktrace não são payloads.

## Limites e recursos

| Recurso | Limite atual |
|---|---|
| Página de metadados | default 25; 1..100; offset 0..10000 |
| Filhos de metadados | 1000 por coleção; erro em excesso |
| Metadados por documento | 250 operações; 10000 linhas percorridas |
| Documento GraphQL | 20000 caracteres; profundidade 12; complexidade 10000 |
| Mutation | até 8 comandos selecionados |
| SQL | até 20000 caracteres; limit default 100, 1..1000 |
| Statement timeout | default 10 s; 1..30 s |
| Deadline GraphQL | 10 s, cooperativo |
| Resultado SQL | 64 colunas; 20000 células/documento; 1000000 caracteres de valores de células |
| Célula | 4096 caracteres ou bytes, flag e encoding explícitos |

Os limites de SQL são constantes na implementação; os limites genéricos têm hooks
protegidos. Alterá-los exige revisão/testes, não configuração arbitrária do modelo.
A complexidade estima cardinalidade de SQL no pai e células/colunas separadamente;
os budgets medem trabalho real. Uma seleção ampla pode ser recusada mesmo com
limit válido; reduza linhas/campos.

Catalog/schema em tables são filtros JDBC: null/omissão pode ampliar a descoberta
conforme o driver. Use filtros explícitos para restringir a base/schema desejados.
JDBC getTables recebe filtros diretamente, percorre offset e para após limit+1;
sem lista completa nem count forçado. Ordenação entre páginas depende do driver e
de um catálogo estável. SQL setMaxRows(limit+1) permite lookahead; returnedRowCount
é o número entregue, hasNextPage revela uma linha adicional. totalCount é null;
não há COUNT auxiliar. Colunas apenas não exigem contagem/varredura das linhas.

Statement.setQueryTimeout é configurado antes de SQL; falta explícita de suporte
impede execução. O driver precisa cumprir o timeout. A deadline cooperativa não
interrompe uma chamada JDBC bloqueada. Interrupção detectada na iteração tenta
Statement.cancel; ResultSet/Statement próprios fecham, Connection do NetBeans não.
Os resolvers de metadados/SQL não fecham Connection nem alteram autocommit.
O comando disconnect delega explicitamente ao ConnectionManager a gestão da conexão.

## Exemplos cobertos pelos testes

Descoberta, via Database.query:

```graphql
{ connections { items { id displayName connected } offset limit hasNextPage } }
```

Metadados, via Database.query (fixture sintética):

```graphql
{ connection(name:"ANAHATA") {
  tables(limit:1) { items { tableName columns { columnName } } }
} }
```

A suíte DatabaseGraphQLTest verifica `getPrimaryKeys` com `never()` nesta seleção.
As demais seleções de PK, FK, paginação e identidades homônimas permanecem cobertas.

SQL autorizado, via Database.mutate (driver simulado):

```graphql
mutation {
  executeQuery(connection:"ANAHATA",sql:"SELECT 1",limit:2) { success }
}
```

DatabaseTest verifica execução do SQL e `never()` para getMetaData, next e getObject;
verifica close em cursor/statement e `never()` em Connection.close.

```graphql
mutation($target:ID!,$sql:String!,$include:Boolean!) {
  chosen:executeQuery(connection:$target,sql:$sql,limit:2) { ...Output }
  skip:executeStatement(connection:$target,sql:"UPDATE anahata SET enabled=1")
    @include(if:$include) { success }
}
fragment Output on SqlQueryInfo { rows { cells { alias:value } } }
```

Teste usa include=false e verifica que executeUpdate não acontece. SQL e tabelas
são fixtures; os exemplos não autorizam DML em banco real.

## Desenvolvimento com NetBeans aberto

Teste/compile/package numa cópia externa de validação, usando POM explícito e
um user.home isolado para JVMs de teste. Não limpe os targets que a IDE em execução
usa como origem dos módulos. Evidências e patches ficam fora do repositório Maven.
Empacotar NBM não significa instalar: instalação e smoke test da nova versão são
etapas separadas. Não houve instalação nesta execução, chamadas a provedores ou
SQL em banco real. Testes mockados provam chamadas externas selecionadas; não
substituem uma medição de desempenho ou teste de instalação/classloader real.
