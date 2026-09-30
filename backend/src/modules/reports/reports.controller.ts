import { Controller, Get, Param, Query, Res } from '@nestjs/common';
import { Response } from 'express';
import { Between, Repository } from 'typeorm';
import { InjectRepository } from '@nestjs/typeorm';
import { Transaction } from '../transactions/transaction.entity';
import { Person } from '../persons/person.entity';
import { CurrentUser } from '../../common/guards/jwt-auth.guard';
import { User } from '../auth/user.entity';
import * as ExcelJS from 'exceljs';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';

@Controller('reports')
export class ReportsController {
  constructor(
    @InjectRepository(Transaction) private readonly txs: Repository<Transaction>,
    @InjectRepository(Person) private readonly persons: Repository<Person>,
  ) {}

  @Get('user/:personId')
  async userReport(
    @CurrentUser() u: User,
    @Param('personId') personId: string,
    @Query('format') format: 'pdf' | 'xlsx' | 'csv' = 'xlsx',
    @Query('fromDate') fromDate?: string,
    @Query('toDate') toDate?: string,
    @Res() res?: Response,
  ) {
    const person = await this.persons.findOne({ where: { id: personId, ownerId: u.id } });
    const list = await this.queryTxs(u.id, { personId, fromDate, toDate });
    const personMap: Record<string, Person> = person ? { [person.id]: person } : {};
    return this.render(format, res ?? ({} as Response), {
      title: `Отчет пользователя — ${person?.name ?? personId}`,
      rows: list,
      personMap,
    });
  }

  @Get('summary')
  async summary(
    @CurrentUser() u: User,
    @Query('format') format: 'pdf' | 'xlsx' | 'csv' = 'xlsx',
    @Query('fromDate') fromDate?: string,
    @Query('toDate') toDate?: string,
    @Res() res?: Response,
  ) {
    const allPersons = await this.persons.find({ where: { ownerId: u.id } });
    const personMap = Object.fromEntries(allPersons.map((p) => [p.id, p]));
    const list = await this.queryTxs(u.id, { fromDate, toDate });
    const summary = allPersons.map((p) => {
      const ptxs = list.filter((t) => t.personId === p.id);
      const recv = ptxs.filter((t) => t.type === 'RECEIVED').reduce((s, t) => s + t.amount, 0);
      const iss = ptxs.filter((t) => t.type === 'ISSUED').reduce((s, t) => s + t.amount, 0);
      return {
        name: p.name,
        phones: p.phones.join(', '),
        status: p.status,
        received: recv,
        issued: iss,
        balance: recv - iss,
        count: ptxs.length,
      };
    });
    return this.renderSummary(format, res ?? ({} as Response), summary);
  }

  @Get('transactions')
  async transactions(
    @CurrentUser() u: User,
    @Query('format') format: 'pdf' | 'xlsx' | 'csv' = 'xlsx',
    @Query('fromDate') fromDate?: string,
    @Query('toDate') toDate?: string,
    @Query('personId') personId?: string,
    @Res() res?: Response,
  ) {
    const allPersons = await this.persons.find({ where: { ownerId: u.id } });
    const personMap = Object.fromEntries(allPersons.map((p) => [p.id, p]));
    const list = await this.queryTxs(u.id, { personId, fromDate, toDate });
    return this.render(format, res ?? ({} as Response), {
      title: 'Отчет по транзакциям',
      rows: list,
      personMap,
    });
  }

  private async queryTxs(
    ownerId: string,
    opts: { personId?: string; fromDate?: string; toDate?: string },
  ) {
    const where: any = { ownerId };
    if (opts.personId) where.personId = opts.personId;
    if (opts.fromDate && opts.toDate) {
      where.date = Between(Number(opts.fromDate), Number(opts.toDate));
    }
    return this.txs.find({ where, order: { date: 'DESC', time: 'DESC' } });
  }

  private async render(
    format: 'pdf' | 'xlsx' | 'csv',
    res: Response,
    data: { title: string; rows: Transaction[]; personMap: Record<string, Person> },
  ) {
    const plain = data.rows.map((t) => ({
      Дата: new Date(Number(t.date)).toLocaleDateString('ru-RU'),
      Тип: t.type === 'RECEIVED' ? 'Получение средств' : 'Выдача средств',
      Контакт: data.personMap[t.personId]?.name ?? t.personId,
      Назначение: t.purpose ?? '',
      Сумма: t.amount,
      Валюта: t.currency,
      Примечание: t.note ?? '',
    }));
    if (format === 'xlsx') {
      const wb = new ExcelJS.Workbook();
      const ws = wb.addWorksheet('Отчет');
      ws.addRow(Object.keys(plain[0] ?? {}));
      plain.forEach((r) => ws.addRow(Object.values(r)));
      const buf = await wb.xlsx.writeBuffer();
      res.setHeader(
        'Content-Type',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      );
      res.setHeader('Content-Disposition', `attachment; filename=report.xlsx`);
      return res.send(Buffer.from(buf));
    } else if (format === 'csv') {
      const header = Object.keys(plain[0] ?? {}).join(';');
      const body = plain
        .map((r) => Object.values(r).map((v) => `"${String(v)}"`).join(';'))
        .join('\n');
      res.setHeader('Content-Type', 'text/csv; charset=utf-8');
      res.setHeader('Content-Disposition', `attachment; filename=report.csv`);
      return res.send('\uFEFF' + header + '\n' + body);
    } else {
      const doc = new jsPDF();
      doc.setFontSize(16);
      doc.text(data.title, 14, 20);
      const head = [Object.keys(plain[0] ?? {})];
      const body = plain.map((r) => Object.values(r));
      autoTable(doc, { head, body, startY: 30, styles: { fontSize: 9 } });
      res.setHeader('Content-Type', 'application/pdf');
      res.setHeader('Content-Disposition', `attachment; filename=report.pdf`);
      return res.send(Buffer.from(doc.output('arraybuffer')));
    }
  }

  private async renderSummary(
    format: 'pdf' | 'xlsx' | 'csv',
    res: Response,
    rows: {
      name: string;
      phones: string;
      status: string;
      received: number;
      issued: number;
      balance: number;
      count: number;
    }[],
  ) {
    const plain = rows.map((r) => ({
      Имя: r.name,
      Телефоны: r.phones,
      Статус: r.status,
      Получено: r.received,
      Выдано: r.issued,
      Баланс: r.balance,
      Операций: r.count,
    }));
    if (format === 'xlsx') {
      const wb = new ExcelJS.Workbook();
      const ws = wb.addWorksheet('Сводный отчет');
      ws.addRow(Object.keys(plain[0] ?? {}));
      plain.forEach((r) => ws.addRow(Object.values(r)));
      const buf = await wb.xlsx.writeBuffer();
      res.setHeader(
        'Content-Type',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      );
      res.setHeader('Content-Disposition', `attachment; filename=summary.xlsx`);
      return res.send(Buffer.from(buf));
    } else if (format === 'csv') {
      const header = Object.keys(plain[0] ?? {}).join(';');
      const body = plain
        .map((r) => Object.values(r).map((v) => `"${String(v)}"`).join(';'))
        .join('\n');
      res.setHeader('Content-Type', 'text/csv; charset=utf-8');
      res.setHeader('Content-Disposition', `attachment; filename=summary.csv`);
      return res.send('\uFEFF' + header + '\n' + body);
    } else {
      const doc = new jsPDF({ orientation: 'landscape' });
      doc.setFontSize(16);
      doc.text('Сводный отчет пользователей', 14, 18);
      const head = [Object.keys(plain[0] ?? {})];
      const body = plain.map((r) => Object.values(r));
      autoTable(doc, { head, body, startY: 28, styles: { fontSize: 9 } });
      res.setHeader('Content-Type', 'application/pdf');
      res.setHeader('Content-Disposition', `attachment; filename=summary.pdf`);
      return res.send(Buffer.from(doc.output('arraybuffer')));
    }
  }
}
