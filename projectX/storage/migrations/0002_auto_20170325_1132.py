# -*- coding: utf-8 -*-
# Generated by Django 1.10.6 on 2017-03-25 04:32
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('storage', '0001_initial'),
    ]

    operations = [
        migrations.AlterField(
            model_name='item',
            name='time',
            field=models.TimeField(),
        ),
    ]
