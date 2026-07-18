import sqlite3
c = sqlite3.connect('db.sqlite3').cursor()
print('argentina:', c.execute("SELECT * FROM participants WHERE entity_id='argentina'").fetchall())
print('england:', c.execute("SELECT * FROM participants WHERE entity_id='england'").fetchall())
print('event:', c.execute("SELECT * FROM events WHERE id='mock_wc_semi'").fetchall())
print('f1_event:', c.execute("SELECT * FROM events WHERE id='mock_belgium_gp'").fetchall())
