"""initial schema

Revision ID: 0001
Revises: 
Create Date: 2026-09-30 09:36:38.498260
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = '0001'
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table('category',
    sa.Column('name', sa.Text(), nullable=False),
    sa.Column('sort_order', sa.Integer(), nullable=False),
    sa.Column('id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('updated_at', sa.String(length=64), nullable=False),
    sa.Column('deleted', sa.Boolean(), nullable=False),
    sa.Column('server_rev', sa.BigInteger(), nullable=False),
    sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_category_server_rev'), 'category', ['server_rev'], unique=False)
    op.create_table('collection',
    sa.Column('name', sa.Text(), nullable=False),
    sa.Column('sort_order', sa.Integer(), nullable=False),
    sa.Column('id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('updated_at', sa.String(length=64), nullable=False),
    sa.Column('deleted', sa.Boolean(), nullable=False),
    sa.Column('server_rev', sa.BigInteger(), nullable=False),
    sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_collection_server_rev'), 'collection', ['server_rev'], unique=False)
    op.create_table('course',
    sa.Column('name', sa.Text(), nullable=False),
    sa.Column('sort_order', sa.Integer(), nullable=False),
    sa.Column('id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('updated_at', sa.String(length=64), nullable=False),
    sa.Column('deleted', sa.Boolean(), nullable=False),
    sa.Column('server_rev', sa.BigInteger(), nullable=False),
    sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_course_server_rev'), 'course', ['server_rev'], unique=False)
    op.create_table('photo',
    sa.Column('recipe_id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('sort_order', sa.Integer(), nullable=False),
    sa.Column('sha256', sa.String(length=64), nullable=False),
    sa.Column('mime', sa.String(length=64), nullable=False),
    sa.Column('width', sa.Integer(), nullable=True),
    sa.Column('height', sa.Integer(), nullable=True),
    sa.Column('id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('updated_at', sa.String(length=64), nullable=False),
    sa.Column('deleted', sa.Boolean(), nullable=False),
    sa.Column('server_rev', sa.BigInteger(), nullable=False),
    sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_photo_recipe_id'), 'photo', ['recipe_id'], unique=False)
    op.create_index(op.f('ix_photo_server_rev'), 'photo', ['server_rev'], unique=False)
    op.create_table('recipe',
    sa.Column('title', sa.Text(), nullable=False),
    sa.Column('description', sa.Text(), nullable=True),
    sa.Column('source_name', sa.Text(), nullable=True),
    sa.Column('source_url', sa.Text(), nullable=True),
    sa.Column('servings_text', sa.Text(), nullable=True),
    sa.Column('servings_count', sa.Integer(), nullable=True),
    sa.Column('prep_min', sa.Integer(), nullable=True),
    sa.Column('cook_min', sa.Integer(), nullable=True),
    sa.Column('total_min', sa.Integer(), nullable=True),
    sa.Column('ingredients_text', sa.Text(), nullable=True),
    sa.Column('directions_text', sa.Text(), nullable=True),
    sa.Column('notes', sa.Text(), nullable=True),
    sa.Column('nutrition', sa.JSON().with_variant(postgresql.JSONB(astext_type=sa.Text()), 'postgresql'), nullable=True),
    sa.Column('rating', sa.Integer(), nullable=False),
    sa.Column('is_favourite', sa.Boolean(), nullable=False),
    sa.Column('course_ids', sa.JSON().with_variant(postgresql.JSONB(astext_type=sa.Text()), 'postgresql'), nullable=False),
    sa.Column('category_ids', sa.JSON().with_variant(postgresql.JSONB(astext_type=sa.Text()), 'postgresql'), nullable=False),
    sa.Column('collection_ids', sa.JSON().with_variant(postgresql.JSONB(astext_type=sa.Text()), 'postgresql'), nullable=False),
    sa.Column('import_ref', sa.String(length=64), nullable=True),
    sa.Column('id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('updated_at', sa.String(length=64), nullable=False),
    sa.Column('deleted', sa.Boolean(), nullable=False),
    sa.Column('server_rev', sa.BigInteger(), nullable=False),
    sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_recipe_import_ref'), 'recipe', ['import_ref'], unique=False)
    op.create_index(op.f('ix_recipe_server_rev'), 'recipe', ['server_rev'], unique=False)
    op.create_table('recipe_history',
    sa.Column('id', sa.BigInteger(), autoincrement=True, nullable=False),
    sa.Column('recipe_id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('updated_at', sa.String(length=64), nullable=False),
    sa.Column('data', sa.JSON().with_variant(postgresql.JSONB(astext_type=sa.Text()), 'postgresql'), nullable=False),
    sa.Column('replaced_by_device', sa.Text(), nullable=True),
    sa.Column('saved_at', sa.DateTime(timezone=True), nullable=False),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index('ix_recipe_history_recipe_saved', 'recipe_history', ['recipe_id', 'saved_at'], unique=False)
    op.create_table('rev_counter',
    sa.Column('id', sa.Integer(), nullable=False),
    sa.Column('value', sa.BigInteger(), nullable=False),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_table('user_account',
    sa.Column('id', sa.Integer(), nullable=False),
    sa.Column('username', sa.String(length=128), nullable=False),
    sa.Column('password_hash', sa.Text(), nullable=False),
    sa.PrimaryKeyConstraint('id'),
    sa.UniqueConstraint('username')
    )
    op.create_table('device',
    sa.Column('id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('user_id', sa.Integer(), nullable=False),
    sa.Column('name', sa.Text(), nullable=False),
    sa.Column('token_hash', sa.String(length=64), nullable=False),
    sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
    sa.Column('last_seen_at', sa.DateTime(timezone=True), nullable=True),
    sa.Column('revoked', sa.Boolean(), nullable=False),
    sa.ForeignKeyConstraint(['user_id'], ['user_account.id'], ),
    sa.PrimaryKeyConstraint('id'),
    sa.UniqueConstraint('token_hash')
    )


def downgrade() -> None:
    op.drop_table('device')
    op.drop_table('user_account')
    op.drop_table('rev_counter')
    op.drop_index('ix_recipe_history_recipe_saved', table_name='recipe_history')
    op.drop_table('recipe_history')
    op.drop_index(op.f('ix_recipe_server_rev'), table_name='recipe')
    op.drop_index(op.f('ix_recipe_import_ref'), table_name='recipe')
    op.drop_table('recipe')
    op.drop_index(op.f('ix_photo_server_rev'), table_name='photo')
    op.drop_index(op.f('ix_photo_recipe_id'), table_name='photo')
    op.drop_table('photo')
    op.drop_index(op.f('ix_course_server_rev'), table_name='course')
    op.drop_table('course')
    op.drop_index(op.f('ix_collection_server_rev'), table_name='collection')
    op.drop_table('collection')
    op.drop_index(op.f('ix_category_server_rev'), table_name='category')
    op.drop_table('category')
