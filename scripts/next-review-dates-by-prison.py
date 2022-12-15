#!/usr/bin/env python
import argparse
import datetime

import pandas as pd

# language=postgresql
dates_sql = '''
-- incentives db
WITH prisoners AS (
    SELECT booking_id, prisoner_number
    FROM prisoner_iep_level
    GROUP BY booking_id, prisoner_number
)
SELECT prisoner_number, next_review_date
FROM prisoners
JOIN next_review_date ON prisoners.booking_id = next_review_date.booking_id
;
'''

# language=postgresql
prisoners_sql = '''
-- prisoner money db
SELECT prison_id, prisoner_number
FROM prison_prisonerlocation
WHERE active
;
'''

parser = argparse.ArgumentParser(usage=f'{dates_sql}\n\n{prisoners_sql}')
parser.add_argument('dates', type=argparse.FileType('r'), help='CSV export from incentives database')
parser.add_argument('prisoners', type=argparse.FileType('r'), help='CSV export from prisoner money database')
parser.add_argument('output', type=argparse.FileType('w'), help='CSV to write out')
args = parser.parse_args()

today = datetime.date.today()
today_str = today.strftime("%d/%m/%Y")
today = pd.to_datetime(today)

dates_df = pd.read_csv(args.dates)
dates_df.loc[dates_df['next_review_date'] < '2000-01-01', 'next_review_date'] = '2000-01-01'
dates_df['next_review_date'] = dates_df['next_review_date'].astype('datetime64')

prisoners_df = pd.read_csv(args.prisoners)
prisoners_df.set_index('prisoner_number', inplace=True)

print('Preview of dates:')
print(dates_df)
print('Preview of prisoners:')
print(prisoners_df)

df = pd.merge(dates_df, prisoners_df, on='prisoner_number', how='right')
df = df[df['next_review_date'] < today]

prisons = pd.Index(
    [
        # private beta groups
        'BAI', 'CFI', 'CWI', 'DAI', 'EXI', 'GHI', 'GMI', 'HOI', 'SWI', 'UKI', 'WDI', 'WII', 'WNI', 'WCI', 'WHI'
    ],
    name='prison_id',
)

population_by_prison = prisoners_df.groupby('prison_id').size()
population_by_prison.name = f'Population as of {today_str}'
overdue_by_prison = df.groupby('prison_id').size()
overdue_by_prison.name = f'Overdue as of {today_str}'
table = pd.merge(
    population_by_prison[prisons],
    overdue_by_prison[prisons[prisons.isin(overdue_by_prison.index)]],
    on='prison_id', how='left',
)
table.index.name = 'Prison ID'
table[table.columns[1]].fillna(0, inplace=True)
table = table.convert_dtypes()
print('Population and verdue by prison:')
print(table)
table.to_csv(args.output)
