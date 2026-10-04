import { type Kysely, sql } from 'kysely';

export async function up(db: Kysely<any>): Promise<void> {
  await db.schema
    .alterTable('users')
    .addColumn('auth_source', 'varchar', (col) =>
      col.notNull().defaultTo('local'),
    )
    .execute();

  // password is now optional: ldap-provisioned users never get a local password
  await sql`ALTER TABLE users ALTER COLUMN password DROP NOT NULL`.execute(db);
}

export async function down(db: Kysely<any>): Promise<void> {
  await db.schema.alterTable('users').dropColumn('auth_source').execute();
}
