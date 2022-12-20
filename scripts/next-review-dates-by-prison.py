#!/usr/bin/env python
import argparse
import datetime
import textwrap

import pandas as pd

# language=postgresql
dates_sql = '''
-- prisoner next review dates and current level, including those who've been released
SELECT DISTINCT ON (prisoner_number) booking_id, prisoner_number, iep_code AS level, next_review_date
FROM prisoner_iep_level
JOIN next_review_date USING (booking_id)
ORDER BY prisoner_number, review_time DESC NULLS LAST
;

'''

# language=postgresql
prisoners_sql = '''
-- prisoners currently in prison
SELECT prisoner_number, prison_id
FROM prison_prisonerlocation
WHERE active
;
'''

parser = argparse.ArgumentParser(usage=f'''
./next-review-dates-by-prison.py [-h] dates prisoners output

`dates` is a CSV extract from incentives database using:
{textwrap.indent(dates_sql, "    ")}

`prisoners` is a CSV extract from prisoner money database using:
{textwrap.indent(prisoners_sql, "    ")}
''')

parser.add_argument('dates', type=argparse.FileType('r'), help='CSV export from incentives database')
parser.add_argument('prisoners', type=argparse.FileType('r'), help='CSV export from prisoner money database')
parser.add_argument('output', type=argparse.FileType('w'), help='CSV to write out')
args = parser.parse_args()

today = datetime.date.today()
today_str = today.strftime("%d/%m/%Y")
today = pd.to_datetime(today)

# prisoner next review dates and current level, including those who've been released
dates_df = pd.read_csv(args.dates)
dates_df.set_index('prisoner_number', inplace=True)
# there are some ancient dates (presumably data entry errors) which need conversion to work with datetime64[ns] type
dates_df.loc[dates_df['next_review_date'] < '2000-01-01', 'next_review_date'] = '2000-01-01'
dates_df['next_review_date'] = dates_df['next_review_date'].astype('datetime64[ns]')

# prisoners currently in prison
prisoners_df = pd.read_csv(args.prisoners)
prisoners_df.set_index('prisoner_number', inplace=True)

print('Preview of next review dates:')
print(dates_df)
print('Preview of prisoners:')
print(prisoners_df)

missing_dates = dates_df.join(prisoners_df, how='right')
missing_dates = missing_dates[missing_dates['level'].isna()]
if not missing_dates.empty:
    print('Prisoners with missing incentive level and/or next review date')
    print(missing_dates['prison_id'])

# prisoner next review dates and current level, including only those in prison
joined_df = dates_df.join(prisoners_df, how='inner')
# overdue prisoner next review dates and current level, including only those in prison
overdue_df = joined_df[joined_df['next_review_date'] < today]

levels = pd.DataFrame(
    dict(level=['Basic', 'Standard', 'Enhanced', 'Enhanced 2', 'Enhanced 3']),
    index=pd.Index(
        ['BAS', 'STD', 'ENH', 'EN2', 'EN3'],
        name='code',
    ),
)

# private beta groups
prisons = pd.DataFrame(
    dict(prison_name=[
        'Belmarsh', 'Cardiff', 'Channings Wood', 'Dartmoor', 'Exeter', 'Garth', 'Guys Marsh', 'High Down',
        'Swansea', 'Usk', 'Wakefield', 'Warren Hill', 'Werrington', 'Winchester', 'Woodhill',
    ]),
    index=pd.Index(
        ['BAI', 'CFI', 'CWI', 'DAI', 'EXI', 'GHI', 'GMI', 'HOI', 'SWI', 'UKI', 'WDI', 'WII', 'WNI', 'WCI', 'WHI'],
        name='prison_id',
    ),
)

indices = pd.DataFrame(index=pd.MultiIndex.from_product([prisons.index, levels.index], names=('prison_id', 'level')))

population_by_prison_and_level = joined_df.groupby(['prison_id', 'level']).size()
population_by_prison_and_level.name = f'Population as of {today_str}'
population_by_prison_and_level = population_by_prison_and_level.to_frame()

overdue_by_prison_and_level = overdue_df.groupby(['prison_id', 'level']).size()
overdue_by_prison_and_level.name = f'Overdue as of {today_str}'
overdue_by_prison_and_level = overdue_by_prison_and_level.to_frame()

# all prisons
stats_table = population_by_prison_and_level.join(overdue_by_prison_and_level, how='left')
stats_table[stats_table.columns[1]].fillna(0, inplace=True)
stats_table = stats_table.convert_dtypes()

# private beta prisons
filtered_stats_table = indices.join(stats_table, how='inner')

print('Population and overdue by prison and level:')
print(filtered_stats_table)
filtered_stats_table.to_csv(args.output)

print('Population and overdue by prison only:')
print(filtered_stats_table.groupby('prison_id').sum())
