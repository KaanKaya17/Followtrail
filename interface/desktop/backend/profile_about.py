"""Local notes from the account information shown to the user in Instagram."""
import re

FIELDS = {'joined', 'country', 'usernameChanges'}


def validate(value, latest_month='2099-12'):
    if not isinstance(value, dict) or not value or not value.keys() <= FIELDS:
        return False
    if 'joined' in value:
        joined = value['joined']
        if not isinstance(joined, str) or not re.fullmatch(r'20[0-9]{2}-(0[1-9]|1[0-2])', joined):
            return False
        if not '2010-10' <= joined <= latest_month:
            return False
    if 'country' in value:
        country = value['country']
        if (not isinstance(country, str) or not 0 < len(country) <= 80 or country.strip() != country
                or any(ord(char) < 32 or ord(char) == 127 for char in country)):
            return False
    if 'usernameChanges' in value:
        changes = value['usernameChanges']
        if type(changes) is not int or not 0 <= changes <= 1_000_000:
            return False
    return True
