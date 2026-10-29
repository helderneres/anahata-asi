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
alterar o contexto. Mutation.connect usa a API pública síncrona ConnectionManager.connect somente
fora da EDT, sem abrir diálogo. SUCCEEDED exige observar o estado solicitado;
REQUIRES_INTERACTION pede configuração/ação manual em Services; PENDING indica
outro comando de ciclo de vida em andamento sobre a mesma inscrição. FAILED
representa uma falha confirmada pela API, e UNKNOWN indica conclusão não
confirmada. Ausência imediata de handle não é inferida como falha terminal.
Nenhuma descoberta, getter ou SQL abre diálogo. O registro fraco de comandos
em andamento é local ao processo e não acompanha tentativas iniciadas por outras
partes da IDE. Nenhum comando é repetido automaticamente.

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

## Políticas e modos de execução

Interfaces abertas de leitura em `agi.tool.execution` descrevem ExecutionMode
(getName), ExecutionPolicy (timeout), QueryExecutionPolicy (loadingPolicy),
MutationExecutionPolicy (resultLoadingPolicy) e ResultLoadingPolicy
(defaultPageSize/maxPageSize). As interfaces Mutable correspondentes usam
put<Property>, retornando o próprio tipo A e a mesma instância, sem casts não
verificados. Alterar uma política não autoriza comandos nem altera destinos.

A implementação do Database fica em `database.policy`. A configuração Java do
Toolkit é obtida por `database.getExecutionPolicies()`, sem ferramenta GraphQL
para editá-la. Os métodos put publicam cópias coerentes. Getters de configuração
retornam cópias destacadas; edições só entram em vigor após publicação pelo put
correspondente. Cada chamada resolve uma política efetiva imutável, incluindo
os componentes e mapas de recursos; alterações posteriores não afetam a execução.
Não é necessário rebind para aplicar uma configuração à próxima chamada.

Ordem: defaults comuns → perfil selecionado → operação → recurso → ajustes
explicitamente autorizados da sessão → validação final. Camadas posteriores
substituem propriedades presentes, sem usar indiscriminadamente o menor valor.
Omissão herda; maxPageSize=null remove um máximo herdado; timeout=null remove
o prazo adicional. defaultPageSize=null é inválido. Default e máximo usam Integer;
o default efetivo é obrigatório e positivo. Máximo e timeout são positivos ou
null, e um máximo presente não pode ser menor que o default efetivo.

| Perfil inicial | Metadados: default/máximo | Resultado SQL: default/máximo | Prazo adicional |
|---|---|---|---|
| EXPLORATION | 25 / 100 | 100 / 1000 | 10 s por chamada |
| EXTENDED_WORK | 25 / null | 100 / null | null |

Os valores são perfis iniciais configuráveis por Java, não novos limites universais.
Os nomes do enum são estáveis; IDs de conexão continuam com o mesmo significado.
Um ajuste autorizado de sessão é publicado por putSessionQueryPolicy ou
putSessionMutationPolicy, com autorização decidida pelo chamador confiável; não
há nova infraestrutura de permissão por campo. A importação/exportação JSON
usa mapeamento explícito dos puts, preserva presença/null e exclui ajustes de
sessão autorizados. A persistência Java/Kryo mantém esses ajustes já autorizados.
Não há alteração do mapper global ou anotações JSpecify nesta entrega.

Exemplo de configuração Java, independente de autorização de SQL:

```java
database.getExecutionPolicies()
        .putMode(DatabaseExecutionMode.EXTENDED_WORK)
        .putResourceLoadingPolicy("tables", new DatabaseLoadingPolicy()
                .putDefaultPageSize(50).putMaxPageSize(null));
```

`limit` omitido usa o default efetivo; `limit:null` é inválido. O SDL não fornece
mais defaults estáticos de limit ou timeoutSeconds. As coleções SQL retornadas
por uma Mutation seguem a política de carregamento do retorno: paginá-las não
reduz a abrangência do comando, UPDATE, DELETE ou DDL autorizado.

O prazo é único por chamada. Um timeoutSeconds positivo explícito restringe esse
prazo, medido desde o início da chamada; se vários campos SQL informarem valores,
o menor desses valores explícitos restringe o documento inteiro. O argumento
não prolonga o prazo configurado. Omissão preserva a política; null/zero/negativo
são inválidos. O teto fixo de 30 segundos foi removido. Cada Statement recebe o
tempo restante, arredondado para cima em segundos; frações positivas nunca viram
zero (ilimitado no JDBC). Durações grandes são adaptadas ao int positivo do JDBC.
Prazo null deixa os defaults independentes do driver/banco inalterados.

## Limites de aquisição, estrutura e recursos

Não há estimador, pesos nem rejeição por complexidade prevista. Os controles
abaixo medem estrutura ou aquisição real; não substituem o estimador removido.
EXTENDED_WORK remove o teto de página da política, mas não promete memória,
capacidade do driver ou transporte ilimitados. Pedidos grandes podem alcançar
um limite de aquisição explícito e retornar erro parcial.

| Recurso | Controle preservado ou explicitado |
|---|---|
| Página | default/máximo da política efetiva; GraphQL Int positivo |
| Offset de metadados | 0..10000; varredura consome orçamento real |
| Filhos de metadados não paginados | 1000 por coleção; erro em excesso |
| Metadados por documento | 250 operações; 10000 linhas percorridas |
| Filtros de metadados | 1024 caracteres por filtro; até 16 tipos de tabela |
| Documento GraphQL | 20000 caracteres; profundidade 12; regras nativas de parser/validação |
| Mutation | até 8 comandos selecionados, validados antes dos efeitos |
| SQL | até 20000 caracteres; nenhuma inferência de segurança pelo texto |
| Linhas SQL percorridas | 10000 por documento, incluindo look-ahead; erro explícito em excesso |
| Resultado SQL | 64 colunas; 20000 células/documento; 1000000 caracteres de valores |
| Célula | 4096 unidades de texto ou bytes antes de base64, flag/encoding explícitos |

O orçamento de linhas SQL torna explícita a proteção da materialização agora que
o máximo de página pode estar ausente. Ele não conta ou estima linhas afetadas
por comandos. Também não transforma uma página solicitada em uma página menor
silenciosamente: excesso produz erro, possivelmente após efeitos.

Catalog/schema em tables são filtros JDBC: null/omissão pode ampliar a descoberta
conforme o driver. Use filtros explícitos para restringir a base/schema desejados.
JDBC getTables recebe filtros diretamente, percorre offset e para após limit+1;
sem lista completa nem count forçado. Ordenação entre páginas depende do driver
e de um catálogo estável. Os contadores evitam somar offset+limit em int.

SQL usa aritmética long para limit+1; quando necessário usa setLargeMaxRows.
Se o driver não suportar esse hint maior, a iteração continua limitada pela
página/look-ahead e pelos orçamentos reais. returnedRowCount é o número entregue,
hasNextPage revela uma linha adicional e totalCount é null, sem COUNT auxiliar.
Colunas apenas não exigem contagem/varredura das linhas. O maior GraphQL Int
continua sendo 2147483647; ausência de máximo da política não muda esse tipo.

Statement.setQueryTimeout é configurado quando houver prazo; falta explícita de
suporte impede SQL antes de iniciá-lo. O driver precisa cumprir o timeout.
A deadline cooperativa não interrompe uma chamada JDBC bloqueada, nem aguarda
futuros do cache com interrupção forçada. Interrupção detectada na iteração tenta
Statement.cancel. ResultSet/Statement próprios fecham; Connection do NetBeans não.
Resolvers não alteram autocommit. Disconnect delega a gestão ao ConnectionManager.
Nenhum prazo garante cancelamento, exactly-once, transação conjunta ou rollback.
A conexão síncrona pode bloquear no driver/cofre de credenciais; o prazo
cooperativo não a interrompe, e Statement.setQueryTimeout não controla esse passo.

## Diagnóstico de entrada e validação

variables tem contrato canônico de objeto JSON. A string "{}" não é um objeto.
O schema de parâmetro entregue ao provedor anuncia object; o adaptador mantém
esse schema. Não há evidência que justifique normalização silenciosa de strings.
Uma rejeição de tipo retorna diagnóstico seguro no errors do protocolo Java:
phase=ARGUMENT_BINDING, argument=variables, expected=object, received=string,
effects=NOT_STARTED. A verificação também cobre edições antes da execução e
preserva null explícito no objeto. Valores, SQL e credenciais não são reproduzidos.

GraphQL errors conserva categoria, tipo nativo, campo, caminho e coordenadas
numéricas seguros. Por exemplo, columns ou rows sem subcampos produzem
SubselectionRequired antes de iniciar os comandos. Mensagens nativas contendo
literais não são copiadas. NOT_STARTED é usado apenas quando comprovado antes
dos resolvers; um erro de execução/coleta não afirma ausência de efeitos.

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

Mutation com seleção de objetos e variables como objeto, validada e executada
com fixtures (nenhum SQL real):

```graphql
mutation ExecuteSql($conn: ID!, $sql: String!) {
  executeQuery(connection: $conn, sql: $sql, limit: 100) {
    success
    columns { index label }
    rows { cells { columnIndex value } }
    returnedRowCount
    hasNextPage
    elapsedTimeMs
  }
}
```

Argumento da ferramenta:

```json
{"variables":{"conn":"<id-descoberto>","sql":"SELECT 1"}}
```

Conexão explícita, interpretando o estado operacional:

```graphql
mutation { connect(connection:"<id-descoberto>") { success status connected message } }
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
