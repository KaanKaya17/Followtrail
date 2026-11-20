// Pure helpers for the viewer UI (tested in test/views.test.mjs).
import {EXTRAS} from './relationships.js';

const EXPORT_NAME = /^instagram-([A-Za-z0-9._]{1,30})-(\d{4}-\d{2}-\d{2})-[A-Za-z0-9]{8}\.zip$/i;

/** Account and date from Instagram's download name, e.g. instagram-ada-2026-11-01-AbCdEf12.zip. */
export function exportInfo(fileName) {
  const match = EXPORT_NAME.exec(fileName || '');
  if (!match) return {account: null, date: null};
  const date = new Date(match[2] + 'T00:00:00Z');
  return {account: match[1].toLowerCase(), date: Number.isNaN(date.getTime()) ? null : match[2]};
}

/** Every list the viewer offers, in display order. Missing lists are null. */
export function listViews(result) {
  const followers = result.followers, following = result.following;
  const views = [['followers', followers], ['following', following]];
  if (followers && following) {
    const followerNames = new Set(followers.map(row => row.username));
    const followingNames = new Set(following.map(row => row.username));
    views.push(['notBack', following.filter(row => !followerNames.has(row.username))]);
    views.push(['youNotBack', followers.filter(row => !followingNames.has(row.username))]);
    views.push(['mutual', following.filter(row => followerNames.has(row.username))]);
  }
  for (const key of Object.keys(EXTRAS)) if (result.extras[key]) views.push([key, result.extras[key].rows]);
  return views;
}

export function filterRows(rows, query, order) {
  const needle = query.trim().toLowerCase().replace(/^@/, '');
  let result = needle ? rows.filter(row => row.username.includes(needle)) : rows.slice();
  if (order === 'newest' || order === 'oldest') {
    const sign = order === 'newest' ? -1 : 1;
    result.sort((a, b) => (a.timestamp === null) - (b.timestamp === null) || sign * ((a.timestamp ?? 0) - (b.timestamp ?? 0)));
  }
  return result;
}

// Spreadsheet apps run cells that start with these characters as formulas.
function cell(value) {
  const text = value === null || value === undefined ? '' : String(value);
  const safe = /^[=+\-@\t\r\n]/.test(text) ? "'" + text : text;
  return '"' + safe.replace(/"/g, '""') + '"';
}

export function utcDay(seconds) {
  return seconds ? new Date(seconds * 1000).toISOString().slice(0, 10) : '';
}

export function toCsv(header, rows) {
  return '﻿' + [header, ...rows].map(row => row.map(cell).join(',')).join('\r\n') + '\r\n';
}
