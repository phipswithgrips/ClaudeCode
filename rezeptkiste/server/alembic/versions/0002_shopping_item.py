"""shopping list

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-06 11:30:00
"""

from alembic import op
import sqlalchemy as sa

revision = '0002'
down_revision = '0001'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table('shopping_item',
    sa.Column('text', sa.Text(), nullable=False),
    sa.Column('checked', sa.Boolean(), nullable=False),
    sa.Column('recipe_id', sa.String(length=64), nullable=True),
    sa.Column('recipe_title', sa.Text(), nullable=True),
    sa.Column('sort_order', sa.BigInteger(), nullable=False),
    sa.Column('id', sa.Uuid(as_uuid=False), nullable=False),
    sa.Column('updated_at', sa.String(length=64), nullable=False),
    sa.Column('deleted', sa.Boolean(), nullable=False),
    sa.Column('server_rev', sa.BigInteger(), nullable=False),
    sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
    sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_shopping_item_server_rev'), 'shopping_item', ['server_rev'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_shopping_item_server_rev'), table_name='shopping_item')
    op.drop_table('shopping_item')
