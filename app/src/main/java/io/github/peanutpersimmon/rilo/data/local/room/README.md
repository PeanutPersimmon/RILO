# Room boundary

The active database file is `rilo.db`. `RidoDatabase` remains an internal class name; its exported version 1 schema is RILO's first schema and contains only Event, Session, Category, Keyword Rule, and Processed Command data. The pre-release RIDO migration history is not supported. Future published schema changes require an explicit migration and migration tests. UI and ViewModels must not access DAOs directly.
