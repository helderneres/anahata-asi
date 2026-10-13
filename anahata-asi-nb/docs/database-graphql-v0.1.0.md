# Database Tool GraphQL — implementação v0.1.0

## Estado local e referências

Localização atual do checkout (realocação informada em 10/10/2026): `C:\Users\Helder\Documents\NetBeansProjects\Personal\anahata\anahata-asi`.

Registro da verificação inicial, anterior à realocação: checkout em `C:\Users\Helder\Documents\NetBeansProjects\Personal\anahata-asi`, branch `feat.service-database-tool`, HEAD `dffdba8cde5ba885f7ffd9e73c7c688a39d1e037`. O checkout estava limpo; a referência remota foi confirmada com `git ls-remote` no mesmo commit. A pasta aberta na conversa era SPIAA, por isso o trabalho foi direcionado ao checkout Anahata encontrado. Não houve mudança de branch, commit, push, PR ou acesso a banco real.

Foram consultados no Drive os documentos [Concepção v0.1.0](https://docs.google.com/document/d/1qs4PgkZGzgKNFAr9cDqYBrU1AfcTQkl58mcnurh8c98/edit) e [Arquitetura GraphQL v0.1.0](https://docs.google.com/document/d/1rh70QcvRHh05fnKytc6jRhbKuGZ13CIZX4hdZUc4FaM/edit), além dos respectivos arquivos Markdown da mesma pasta de domínio. O espelho esperado `G:\Meu Drive\.work\ia-work\personal.essc.anahata.work` não pôde ser confirmado nesta sessão; a leitura veio diretamente do Drive conectado.

O POM define release Java 21. A validação utiliza Oracle JDK 21.0.3 e Maven 3.9.16 instalado com o Apache NetBeans. O módulo NB declara `RELEASE310`; comentários antigos do POM e partes de `anahata.md` citam versões diferentes. Foram preservadas as versões reais do código. `LICENSE` é Apache 2.0; o repositório também contém ASL v108 e cabeçalhos dessa licença, mantidos nos arquivos novos. graphql-java 24.3 foi resolvido pelo Maven, sua API foi compilada e sua licença é MIT.

## Implementação e arquivos

No core, `agi.tool.graphql` contém `GraphQLToolkit`, `GraphQLExecutionInfo`, `GraphQLErrorInfo`, `GraphQLRequestContext` e `SafeGraphQLException`. `agi.tool.query` contém `FilterMatcher`, `ToolFilter` e `ToolProjector`. Os POMs pai/core incluem graphql-java 24.3, sem dependência NetBeans na infraestrutura genérica.

No NB, a própria `Database` estende `GraphQLToolkit`, mantém `ConnectionListener` e chama o lifecycle pai. `DatabaseGraphQL` reúne SDL, wiring nativo e fontes privadas de resolução. Foram adicionados os modelos concretos Lombok `MetadataPageInfo`, `PrimaryKeyInfo` e `TableRelationshipInfo`; `TableInfo` ganhou `catalog`. Os DTOs existentes e os métodos legados continuam presentes. `NetBeansAgiConfig` já registrava `Database.class` e não precisou mudar. `JavaObjectToolkit` já percorre as superclasses e registra `Database.schema` e `Database.query` herdados.

Testes novos: `GraphQLToolkitTest`, `GraphQLRequestContextTest`, `ToolFilterTest` e `DatabaseGraphQLTest`. A suíte `DatabaseTest` original foi preservada. O contrato compartilhado foi documentado em `anahata-asi-intellij/anahata.md`; não foi criada integração Database em IntelliJ.

O protocolo continua sendo `@AgiTool`: `schema()` retorna o SDL exato, e `query(String, Map<String,Object>)` executa no motor embutido. As ferramentas mantêm a permissão padrão `PROMPT`. Não há servidor HTTP, SQL arbitrário no schema, mutation, subscription, novo SPI ou planejador genérico de seleção.

## Schema final

O contrato abaixo foi extraído do SDL da implementação. `schema()` é a fonte de descoberta durante o uso.

```graphql
type Query {
  connections(nameContains: String, connected: Boolean, offset: Int = 0, limit: Int = 25): DatabaseConnectionPage!
  connection(name: String!): DatabaseConnectionInfo
}
type DatabaseConnectionPage {
  items: [DatabaseConnectionInfo!]!
  offset: Int!
  limit: Int!
  hasNextPage: Boolean!
  totalCount: Int
}
type DatabaseConnectionInfo {
  displayName: String
  driverClass: String
  schema: String
  connected: Boolean!
  metadata: DatabaseMetadataInfo
  tables(catalog: String, schema: String, tableNamePattern: String = "%", types: [String!] = ["TABLE", "VIEW"], offset: Int = 0, limit: Int = 25): TablePage
}
type DatabaseMetadataInfo {
  databaseProductName: String
  databaseProductVersion: String
  driverName: String
  driverVersion: String
}
type TablePage {
  items: [TableInfo!]!
  offset: Int!
  limit: Int!
  hasNextPage: Boolean!
  totalCount: Int
}
type TableInfo {
  catalog: String
  schema: String
  tableName: String
  tableType: String
  remarks: String
  columns(columnNamePattern: String = "%"): [ColumnInfo!]
  primaryKeys: [PrimaryKeyInfo!]
  importedRelationships: [TableRelationshipInfo!]
  exportedRelationships: [TableRelationshipInfo!]
}
type ColumnInfo {
  columnName: String
  dataType: String
  columnSize: Int
  nullable: Boolean
  primaryKey: Boolean
  autoIncrement: Boolean
}
type PrimaryKeyInfo {
  catalog: String
  schema: String
  tableName: String
  columnName: String
  keySequence: Int
  keyName: String
}
type TableRelationshipInfo {
  primaryKeyCatalog: String
  primaryKeySchema: String
  primaryKeyTable: String
  primaryKeyColumn: String
  foreignKeyCatalog: String
  foreignKeySchema: String
  foreignKeyTable: String
  foreignKeyColumn: String
  keySequence: Int
  updateRule: Int
  deleteRule: Int
  foreignKeyName: String
  primaryKeyName: String
  deferrability: Int
}
```

## Seleção, filtros e recursos

`Query.connections` aplica filtros tipados em memória sobre o pequeno conjunto do `ConnectionManager`, ordena por display name e pagina. `Query.connection` usa o nome exato; ausência retorna `null`, e nomes duplicados geram erro acionável. URL, usuário e senha não são campos do schema. Um display name informado pelo usuário pode conter informações sensíveis; ele só aparece na descoberta solicitada. O contexto RAG automático expõe contagens de conexões registradas/ativas, sem nomes, URLs ou usuários. A listagem legada também deixou de preencher URL e usuário.

`metadata` inspeciona `getSelectionSet()` e chama somente os getters JDBC selecionados; selecionar apenas `__typename` não consulta o banco. `tables` passa catalog/schema/pattern/types ao `DatabaseMetaData.getTables`, pula offset e para após uma linha de look-ahead. Não materializa o conjunto completo nem força contagem; `totalCount` de tabelas é `null`. Se somente offset/limit/totalCount forem selecionados, nem `getTables` é chamado. Para `hasNextPage` sem items, as linhas são percorridas sem construir DTOs de tabela.

`columns`, `primaryKeys` e as duas direções de relacionamento são operações independentes. `columns { columnName }` não chama `getPrimaryKeys`. `Column.primaryKey` busca PK somente quando selecionado e reutiliza o resultado de `Table.primaryKeys` para a mesma conexão/catalog/schema/tabela, inclusive em aliases. O cache pertence a uma única execução, armazena falhas também e nunca persiste handles JDBC. Fragments e diretivas seguem a seleção resolvida pelo graphql-java.

Os resolvers filhos recebem a identidade completa informada por `getTables`. Nos métodos JDBC filhos, catalog/schema ausentes viram string vazia para representar exatamente ausência de qualificador; `null` nos argumentos de `tables` mantém sua semântica de filtro sem restrição. Schema e tabela literais são escapados no `getColumns`, cuja API recebe patterns. Nome de tabela ausente gera erro antes de consultar columns/PK/FK. Drivers que não suportam escape confiável recebem erro acionável.

`ColumnInfo` manteve seus getters booleanos primitivos por compatibilidade. As fontes privadas transportam Boolean/Integer nullable para GraphQL, preservando NULL e flags JDBC desconhecidas. PK/FK preservam sequência, identidades, nomes e regras. `PrimaryKeyInfo.keyName` corresponde a `PK_NAME`; esse nome substitui o `constraintName` referencial. Os campos de identidade e as listas JDBC são nullable para aceitar drivers incompletos e respostas parciais.

As conexões NetBeans nunca são fechadas. ResultSets criados pelos resolvers são fechados inclusive em falhas ou excesso de linhas. Conexão inativa gera `ConnectionInactive`, sem diálogo ou conexão automática. Erros JDBC são classificados como `DatabaseMetadataFailure`; detalhes e stacktraces ficam nos logs locais, não na resposta ao agente. O transporte só contém mapas/listas/scalars selecionados e DTOs de erro sem tipos de graphql-java. Os métodos SQL legados continuam separados, sujeitos às permissões existentes; foi removida a alegação de segurança automática de SQL bruto.

## Limites e configuração

| Limite | Valor inicial | Local de configuração |
|---|---:|---|
| offset | 0 a 10.000 | `DatabaseGraphQL.MAX_OFFSET` |
| limit | padrão 25; máximo 100 | SDL e constantes de `GraphQLToolkit` |
| Linhas por lista columns/PK/FK | 1.000 | `DatabaseGraphQL.MAX_CHILD_ROWS` |
| Linhas JDBC percorridas por requisição | 10.000, incluindo skip/look-ahead | `DatabaseGraphQL.MAX_REQUEST_ROWS` |
| Comprimento por filtro | 1.024 caracteres | `DatabaseGraphQL.MAX_FILTER_LENGTH` |
| Tipos de tabela por filtro | 16 | `DatabaseGraphQL.MAX_TABLE_TYPES` |
| Documento GraphQL | 20.000 caracteres | hook `getMaxQueryCharacters()` |
| Profundidade | 12 | hook `getMaxQueryDepth()` |
| Complexidade | 10.000 | hook `getMaxQueryComplexity()` |
| Aquisições/blocos de leitura de metadata | 250 | hook `getMaxMetadataOperations()` |
| Prazo cooperativo | 10 segundos | hook `getMaxDurationMillis()` |

A complexidade usa argumentos `limit` já coerced, multiplica fan-out de listas e limita custos intermediários acima do teto configurado para impedir overflow. Listas sem paginação usam estimativa de 100 elementos; o orçamento de linhas restringe a quantidade real. Os limites NB são constantes nesta primeira versão, sem tela de configuração ou propriedades globais novas. Os hooks core podem ser ajustados por implementações. O orçamento de metadata mede blocos de aquisição; um bloco combinado de metadata pode chamar vários getters JDBC selecionados.

O prazo é verificado antes/depois das operações e durante iteração. Ele não interrompe um driver preso dentro de uma chamada JDBC. Não se altera network timeout nem se fecha a conexão da IDE para forçar cancelamento. A paginação de tabelas segue a ordem devolvida pelo driver; mudanças concorrentes ou diferenças de drivers podem alterar páginas. Não há snapshot entre requisições.

## Exemplos exercitados por testes com mocks

```graphql
{ connection(name: "MKA") { tables(limit: 10) { items { tableName } } } }
```

Esse caso verifica `never()` para `getColumns`, `getPrimaryKeys`, `getImportedKeys` e `getExportedKeys`, além do fechamento do ResultSet e da preservação da Connection.

```graphql
{ connection(name: "MKA") {
  tables(limit: 10) { items { tableName columns { columnName } } }
} }
```

Esse caso executa `getColumns` apenas para as tabelas retornadas e verifica `getPrimaryKeys(..., never())`.

```graphql
{ connection(name: "MKA") { tables(limit: 1) { items {
  columns { columnName primaryKey }
  primaryKeys { catalog schema tableName columnName keySequence keyName }
} } } }
```

Esse caso prova uma única aquisição de PK por tabela, compartilhada entre colunas e lista de PK. Testes separados selecionam imported/exported e verificam que a outra direção não é consultada.

```graphql
query Tables($catalog: String!, $schema: String!, $pattern: String!) {
  connection(name: "MKA") {
    tables(catalog: $catalog, schema: $schema,
           tableNamePattern: $pattern, types: ["VIEW"], limit: 10) {
      items { catalog schema tableName }
    }
  }
}
```

Variáveis exercitadas: `{"catalog":"catalog-a","schema":"tenant_%","pattern":"invoice%"}`. Os argumentos são verificados diretamente em `getTables`. Há também testes de paginação look-ahead, tabelas homônimas, escape literal de schema/tabela e pattern de coluna.

## Validação e evidências

`mvn -pl anahata-asi-nb -am verify` terminou com **BUILD SUCCESS** nos dez módulos do reactor selecionado. Foram executados **84 testes**, com **zero falhas, erros ou testes ignorados**:

| Suíte/módulo | Testes | Resultado |
|---|---:|---|
| Core, incluindo lifecycle/validação/cache/abstrações | 36 | aprovados |
| Gemini, regressão das dependências do reactor | 7 | aprovados |
| DatabaseGraphQLTest | 31 | aprovados |
| DatabaseTest existente | 10 | aprovados |

O NBM foi gerado em `anahata-asi-nb/target/nbm/anahata-asi-nb-1.4.0-SNAPSHOT.nbm`. A inspeção do ZIP e do manifest confirmou `graphql-java.jar`, `java-dataloader.jar` e `reactive-streams.jar` dentro do pacote e em seu `Class-Path`. Não foi alterado o POM NB para duplicar dependências: o empacotamento existente de bibliotecas transitivas já atendeu ao contrato.

A prova de trabalho negativo usa mocks JDBC e `verify(..., never())`, cobrindo campos não selecionados, PK condicional, import/export independentes, tabelas descartadas pela paginação, campos puramente de paginação e identidade ausente. Também há testes de resposta parcial, flags nullable, fechamento em erro/overflow, aliases/fragments, variáveis, credenciais ausentes e listener sem duplicação.

A baseline inicial core passou com 19 testes. Ocorreram falhas intermediárias de compilação durante escrita concorrente, falta de memória nativa na JVM e inicialização do Mockito por autoanexação de Byte Buddy; esses resultados não foram tratados como aprovação. A instrumentação foi instalada na inicialização da JVM, conforme o mecanismo documentado pelo [Mockito](https://javadoc.io/static/org.mockito/mockito-core/5.2.0/org/mockito/Mockito.html#39), sem alterar suas dependências de baseline. As primeiras fixtures novas também precisaram corrigir criação de mocks durante stubbing e usar páginas pequenas para respeitar a complexidade. O verify final acima inclui todas as correções.

Comando de reprodução usado no PowerShell, a partir de qualquer diretório:

```powershell
$repoPath = 'C:\Users\Helder\Documents\NetBeansProjects\Personal\anahata\anahata-asi'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:MAVEN_OPTS = '-Xmx512m -XX:ActiveProcessorCount=2'
$env:JAVA_TOOL_OPTIONS = '-Xmx384m -XX:ActiveProcessorCount=2 -Dnet.bytebuddy.experimental=true -javaagent:C:\Users\Helder\.m2\repository\net\bytebuddy\byte-buddy-agent\1.14.1\byte-buddy-agent-1.14.1.jar'
& 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd' `
  -f "$repoPath\pom.xml" -pl anahata-asi-nb -am verify '-Dstyle.color=never'
```

O caminho do agente deve corresponder ao artefato efetivamente resolvido no cache Maven da máquina. As variáveis acima pertencem ao processo de teste; não foram persistidas no sistema ou adicionadas ao runtime da IDE.

Também foram usados `git status`, `git branch --show-current`, `git log -1`, `git diff`, `git diff --check`, `git ls-remote --heads origin feat.service-database-tool`, o baseline `-pl anahata-asi-core -am test`, o teste isolado core com heap limitado e a repetição `-pl anahata-asi-nb -am test -Dtest=DatabaseTest,DatabaseGraphQLTest -Dsurefire.failIfNoSpecifiedTests=false`. Essa última opção permite que módulos upstream sem esses testes sejam compilados; a validação definitiva executou toda a suíte do reactor selecionado, sem filtro.

Evidências persistentes da reaplicação validada em 10/10/2026: `C:\Users\Helder\Documents\Codex\hns_anahata-asi_impl\_backups\database-tool\2026-10-10-131538\maven-verify.log` e `validation.json` na mesma pasta, fora do repositório de código. XML/TXT que o próprio Maven gera continuam nos `target/surefire-reports` dos módulos e são descartáveis por Clean. Patches e recursos de validação do Codex devem permanecer nas pastas externas `C:\Users\Helder\Documents\Codex\hns_anahata-asi_impl\patches` e `C:\Users\Helder\Documents\Codex\hns_anahata-asi_impl\_backups\database-tool`. As alterações continuam locais, sem commit/push/PR.

## Riscos residuais e próximos passos

O smoke test na IDE real não foi executado: não houve autorização para usar uma conexão real. Instalar/recarregar o NBM e testar uma conexão de leitura explicitamente autorizada permanece como validação operacional. Os mocks não garantem comportamento de todos os drivers; qualificadores vazios, escape de patterns e flags desconhecidas precisam ser observados em cada driver alvo. O prazo cooperativo não é cancelamento rígido JDBC.

A superfície SQL legada e seu eventual diálogo de conexão foram preservados; uma revisão de permissões/execução SQL fica para evolução independente. Os limites de listas filhas geram erro explícito em excesso, sem truncar silenciosamente; paginação dessas listas e estratégias de batching podem ser avaliadas em outra versão. O schema é descoberto sob demanda, reduzindo contexto RAG permanente.



