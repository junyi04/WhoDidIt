import { useState, useEffect, useCallback } from 'react';
import { Card } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './ui/tabs';
import { ArrowLeft, Trophy, Medal, Award, TrendingUp, Search, Loader2, Shield } from 'lucide-react';
import axios from 'axios';
import { toast } from 'sonner';

const apiClient = axios.create({ baseURL: '/api/ranking', withCredentials: true });

interface RankingPageProps {
    onBack: () => void;
}

// --- 타입 정의 시작 ---

// 1. RankingDto (role 속성 제외)
interface RankingDto {
    userId: number;
    nickname: string;
    score: number;
    totalCases: number;
    successRate: number;
    rank: number; // 순위 (백엔드에서 계산되어 전달)
}

interface RankingState {
    detectives: RankingDto[];
    culprits: RankingDto[];
    clients: RankingDto[];
    police: RankingDto[];
}

// 2. 프론트엔드에서 사용하는 랭킹 데이터 (DTO + rank)
interface RankedUser extends RankingDto {
    rank: number; // 프론트엔드에서 계산되어 추가되는 순위
}

const getRankIcon = (rank: number) => {
    if (rank === 1) return <Trophy className="size-6 text-yellow-500" />;
    if (rank === 2) return <Medal className="size-6 text-gray-400" />;
    if (rank === 3) return <Award className="size-6 text-amber-700" />;
    return <span className="w-6 text-center font-bold">{rank}</span>;
};

const getRankBadge = (rank: number) => {
    if (rank === 1) return <Badge className="bg-yellow-500 hover:bg-yellow-600">1위</Badge>;
    if (rank === 2) return <Badge className="bg-gray-400 hover:bg-gray-500">2위</Badge>;
    if (rank === 3) return <Badge className="bg-amber-700 hover:bg-amber-800">3위</Badge>;
    return <Badge variant="outline">{rank}위</Badge>;
};

const RankingTable = ({ data, roleColor }: { data: RankedUser[], roleColor: string }) => (
    <div className="space-y-3">
        {data.length === 0 ? (
            <div className="text-center text-muted-foreground p-4">
                아직 해당 역할의 랭킹 데이터가 없습니다.
            </div>
        ) : (
            data.map((item) => (
                <Card
                    key={item.userId}
                    className={`p-4 hover:shadow-lg transition-shadow ${
                        item.rank <= 3 ? 'border-2 ' + roleColor : ''
                    }`}
                >
                    <div className="flex items-center gap-4">
                        <div className="flex items-center justify-center w-12">
                            {getRankIcon(item.rank)}
                        </div>
                        <div className="flex-1">
                            <div className="flex items-center gap-3 mb-2">
                                <h3>{item.nickname}</h3>
                                {getRankBadge(item.rank)}
                            </div>
                            <div className="flex gap-6 text-sm text-muted-foreground">
                                <span>총 사건: {item.totalCases}건</span>
                                <span className="flex items-center gap-1">
                                    <TrendingUp className="size-3" />
                                    성공률: {item.successRate.toFixed(1)}%
                                </span>
                            </div>
                        </div>
                        <div className="text-right">
                            <div className="text-2xl font-bold text-primary">{item.score}</div>
                            <div className="text-xs text-muted-foreground">포인트</div>
                        </div>
                    </div>
                </Card>
            ))
        )}
    </div>
);

// --- RankingPage 메인 컴포넌트 ---

export function RankingPage({ onBack }: RankingPageProps) {
    const [allRankings, setAllRankings] = useState<RankingState>({
        detectives: [],
        culprits: [],
        clients: [],
        police: []
    });
    const [loading, setLoading] = useState(true);

    // 🚨 1. 전체 랭킹 조회 API 연동
    const fetchRankings = useCallback(async () => {
        setLoading(true);
        try {
            const [detectives, culprits, clients, police] = await Promise.all([
                apiClient.get<RankingDto[]>('/detectives'),
                apiClient.get<RankingDto[]>('/culprits'),
                apiClient.get<RankingDto[]>('/clients'),
                apiClient.get<RankingDto[]>('/police')
            ]);

            setAllRankings({
                detectives: detectives.data,
                culprits: culprits.data,
                clients: clients.data,
                police: police.data,
            });
        } catch (err: any) {
            toast.error("랭킹 정보를 불러오지 못했습니다.");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchRankings();
    }, [fetchRankings]);

    // 2. 역할별로 랭킹 분리 및 순위 계산 (프론트엔드에서 처리)
    const filterAndRank = (roleFilter: keyof RankingState): RankedUser[] => {
        return allRankings[roleFilter]
            .map((item, index) => ({
                ...item,
                rank: index + 1, // rank 속성 추가
            })) as RankedUser[];
    };

    const detectiveRankings = filterAndRank('detectives');
    const culpritRankings = filterAndRank('culprits');
    const clientRankings = filterAndRank('clients');
    const policeRankings = filterAndRank('police');
    
    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-900 to-slate-800 p-8">
            <div className="max-w-6xl mx-auto">
                {/* Header */}
                <div className="flex items-center gap-4 mb-8">
                    <Button
                        onClick={onBack}
                        variant="outline"
                        className="bg-white/10 border-white/20 text-white hover:bg-white/20"
                    >
                        <ArrowLeft className="size-4 mr-2" />
                        돌아가기
                    </Button>
                    <div>
                        <h1 className="text-white mb-2">명예의 전당</h1>
                        <p className="text-blue-200">최고의 의뢰인, 범인, 탐정, 경찰을 확인하세요</p>
                    </div>
                </div>

                {loading ? (
                    <Card className="p-12 text-center text-blue-500 flex items-center justify-center gap-2">
                        <Loader2 className="animate-spin size-6 mr-2" /> 랭킹 정보 로딩 중...
                    </Card>
                ) : (
                    <Card className="p-6">
                        <Tabs defaultValue="detective" className="w-full">
                            <TabsList className="flex w-full gap-4 mb-6"> {/* flex로 탭을 가로로 정렬 */}
                                <TabsTrigger value="detective" className="flex-1 text-center py-2">
                                    <Search className="size-4" />
                                    탐정 랭킹
                                </TabsTrigger>
                                <TabsTrigger value="culprit" className="flex-1 text-center py-2">
                                    <Award className="size-4" />
                                    범인 랭킹
                                </TabsTrigger>
                                <TabsTrigger value="client" className="flex-1 text-center py-2">
                                    <Trophy className="size-4" />
                                    의뢰인 랭킹
                                </TabsTrigger>
                                <TabsTrigger value="police" className="flex-1 text-center py-2">
                                    <Shield className="size-4" />
                                    경찰 랭킹
                                </TabsTrigger>
                            </TabsList>

                            <TabsContent value="detective">
                                <div className="mb-4">
                                    <h3 className="mb-2">탐정 순위</h3>
                                    <p className="text-sm text-muted-foreground">사건 해결 성공률과 총 해결 건수를 기반으로 한 순위입니다</p>
                                </div>
                                <RankingTable data={detectiveRankings} roleColor="border-purple-500" />
                            </TabsContent>

                            <TabsContent value="culprit">
                                <div className="mb-4">
                                    <h3 className="mb-2">범인 순위</h3>
                                    <p className="text-sm text-muted-foreground">탐정을 속인 성공률과 참여 건수를 기반으로 한 순위입니다</p>
                                </div>
                                <RankingTable data={culpritRankings} roleColor="border-red-500" />
                            </TabsContent>

                            <TabsContent value="client">
                                <div className="mb-4">
                                    <h3 className="mb-2">의뢰인 순위</h3>
                                    <p className="text-sm text-muted-foreground">의뢰한 사건의 해결률과 총 의뢰 건수를 기반으로 한 순위입니다</p>
                                </div>
                                <RankingTable data={clientRankings} roleColor="border-blue-500" />
                            </TabsContent>

                            <TabsContent value="police">
                                <div className="mb-4">
                                    <h3 className="mb-2">경찰 순위</h3>
                                    <p className="text-sm text-muted-foreground">범인 추적 성공률과 총 추적 건수를 기반으로 한 순위입니다</p>
                                </div>
                                <RankingTable data={policeRankings} roleColor="border-green-500" />
                            </TabsContent>
                        </Tabs>
                    </Card>
                )}
            </div>
        </div>
    );
}
